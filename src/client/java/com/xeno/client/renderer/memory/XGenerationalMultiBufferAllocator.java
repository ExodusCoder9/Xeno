package com.xeno.client.renderer.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.util.Util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultimate Multi-Generational Multi-Buffer Allocator for Minecraft Rendering.
 * Combines lock-free bump allocation (Young), indirection handle tables,
 * Two-Level Segregated Fit (TLSF) fragmentation-free storage (Old), telemetry monitoring,
 * and adaptive multi-strategy promotion (Logical $O(1)$ vs Physical SIMD copy).
 * Zero preview or incubating dependencies.
 */
public class XGenerationalMultiBufferAllocator implements IXenoArenaAllocator {

    public record MemoryHandle(int id) {
        public boolean isValid() {
            return id > 0;
        }
    }

    public static class AllocationHandle {
        public final long id;
        public final int handleId;
        public XGenerationalArena arena;
        public long offset;
        public long size;
        public Object ownerTag;
        public final long creationTimeMs;
        public int indexInArena = -1;
        public boolean valid = true;
        public long packedHandle = XenoHandle.NULL_HANDLE;
        public XenoTlsfArena.BlockHeader tlsfBlock = null;
        public byte generationTag; // 0 = Young, 1 = Survivor, 2 = Old

        public AllocationHandle(long id, int handleId, XGenerationalArena arena, long offset, long size, Object ownerTag, long creationTimeMs, byte generationTag) {
            this.id = id;
            this.handleId = handleId;
            this.arena = arena;
            this.offset = offset;
            this.size = size;
            this.ownerTag = ownerTag;
            this.creationTimeMs = creationTimeMs;
            this.generationTag = generationTag;
            if (arena != null) {
                this.packedHandle = XenoHandle.pack(arena.arenaId, offset, size);
            }
        }

        public GpuBuffer getBuffer() {
            return this.arena != null ? this.arena.gpuBuffer : null;
        }

        public MemorySegment getMemorySegment() {
            if (this.arena == null || this.arena.memorySegment == null) return null;
            return this.arena.memorySegment.asSlice(this.offset, this.size);
        }

        public ByteBuffer getByteBuffer() {
            if (this.arena == null || this.arena.directBuffer == null) return null;
            return this.arena.directBuffer.slice((int) this.offset, (int) this.size);
        }

        public XenoGeneration getGeneration() {
            return switch (this.generationTag) {
                case 1 -> XenoGeneration.SURVIVOR;
                case 2 -> XenoGeneration.OLD;
                default -> XenoGeneration.YOUNG;
            };
        }

        public XenoMemoryKind getMemoryKind() {
            return this.arena != null ? this.arena.memoryKind : XenoMemoryKind.GPU_VRAM;
        }
    }

    // Phase 1: Standardized 16-byte Block Tracking Metadata Layout (Off-Heap Handle Table)
    // Offset 0: 4-byte GenTag | Offset 4: 4-byte Size | Offset 8: 8-byte Raw Address
    private static final long HANDLE_METADATA_SIZE = 16L;
    private static final int MAX_HANDLES = 131072; // Up to 128,000 active handles

    private final String namePrefix;
    private final XenoMemoryKind memoryKind;
    private final int gpuUsageFlags;
    private final long youngCapacity;
    private final long survivorCapacity;
    private final long oldCapacity;

    // Handle Table Indirection Layer
    private final Arena tableArena;
    private final MemorySegment handleTableSegment;
    private final AtomicInteger handleIdGenerator = new AtomicInteger(1);
    private final Map<Integer, AllocationHandle> handleMap = new ConcurrentHashMap<>();

    // Arenas & Generation Pools
    private final List<XGenerationalArena> youngArenas = new CopyOnWriteArrayList<>();
    private final List<XGenerationalArena> survivorArenas = new CopyOnWriteArrayList<>();
    private final List<XGenerationalArena> oldArenas = new CopyOnWriteArrayList<>();
    private final AtomicLong allocIdCounter = new AtomicLong(1);

    // Phase 2: Telemetry Monitoring System
    private final AtomicLong allocationsPerTick = new AtomicLong(0);
    private final AtomicLong freesPerTick = new AtomicLong(0);
    private volatile long lastAvailableTickNanos = 16_666_667L;

    public XGenerationalMultiBufferAllocator(
            String namePrefix,
            XenoMemoryKind memoryKind,
            int gpuUsageFlags,
            long youngCapacity,
            long survivorCapacity,
            long oldCapacity
    ) {
        this.namePrefix = namePrefix;
        this.memoryKind = memoryKind;
        this.gpuUsageFlags = gpuUsageFlags;
        this.youngCapacity = youngCapacity;
        this.survivorCapacity = survivorCapacity;
        this.oldCapacity = oldCapacity;

        this.tableArena = Arena.ofShared();
        this.handleTableSegment = this.tableArena.allocate(MAX_HANDLES * HANDLE_METADATA_SIZE, 16);

        this.addNewArena(XenoGeneration.YOUNG, youngCapacity);
    }

    public XGenerationalMultiBufferAllocator(String namePrefix, int gpuUsageFlags, long youngCapacity, long oldCapacity) {
        this(namePrefix, XenoMemoryKind.GPU_VRAM, gpuUsageFlags, youngCapacity, youngCapacity * 2, oldCapacity);
    }

    public static XGenerationalMultiBufferAllocator createCpuOffHeapFFM(String namePrefix, long youngCapacity, long oldCapacity) {
        return new XGenerationalMultiBufferAllocator(namePrefix, XenoMemoryKind.CPU_OFFHEAP_FFM, 0, youngCapacity, youngCapacity * 2, oldCapacity);
    }

    private synchronized XGenerationalArena addNewArena(XenoGeneration gen, long capacity) {
        List<XGenerationalArena> list = getArenaList(gen);
        XGenerationalArena arena;
        if (gen == XenoGeneration.OLD || gen == XenoGeneration.SURVIVOR) {
            arena = new XenoTlsfArena(list.size(), gen, this.memoryKind, this.namePrefix, this.gpuUsageFlags, capacity);
        } else {
            arena = new XGenerationalArena(list.size(), gen, this.memoryKind, this.namePrefix, this.gpuUsageFlags, capacity);
        }
        list.add(arena);
        return arena;
    }

    private List<XGenerationalArena> getArenaList(XenoGeneration gen) {
        return switch (gen) {
            case YOUNG -> this.youngArenas;
            case SURVIVOR -> this.survivorArenas;
            case OLD -> this.oldArenas;
        };
    }

    private long getDefaultCapacity(XenoGeneration gen) {
        return switch (gen) {
            case YOUNG -> this.youngCapacity;
            case SURVIVOR -> this.survivorCapacity;
            case OLD -> this.oldCapacity;
        };
    }

    private void updateHandleTable(int handleId, byte genTag, int size, long rawAddress) {
        long base = (long) (handleId % MAX_HANDLES) * HANDLE_METADATA_SIZE;
        this.handleTableSegment.set(ValueLayout.JAVA_INT, base, genTag & 0xFF);
        this.handleTableSegment.set(ValueLayout.JAVA_INT, base + 4L, size);
        this.handleTableSegment.set(ValueLayout.JAVA_LONG, base + 8L, rawAddress);
    }

    public boolean containsBuffer(GpuBuffer buffer) {
        if (buffer == null || this.memoryKind != XenoMemoryKind.GPU_VRAM) return false;
        for (XGenerationalArena arena : this.youngArenas) {
            if (arena.gpuBuffer == buffer) return true;
        }
        for (XGenerationalArena arena : this.survivorArenas) {
            if (arena.gpuBuffer == buffer) return true;
        }
        for (XGenerationalArena arena : this.oldArenas) {
            if (arena.gpuBuffer == buffer) return true;
        }
        return false;
    }

    @Override
    public synchronized AllocationHandle allocate(long size, Object ownerTag) {
        return this.allocate(size, ownerTag, XenoGeneration.YOUNG);
    }

    public synchronized AllocationHandle allocate(long size, Object ownerTag, XenoGeneration generation) {
        this.allocationsPerTick.incrementAndGet();
        long alignedSize = (size + 15) & ~15;
        long nowMs = Util.getMillis();
        int handleId = this.handleIdGenerator.getAndIncrement();
        byte genTag = (byte) (generation == XenoGeneration.OLD ? 2 : (generation == XenoGeneration.SURVIVOR ? 1 : 0));

        if (generation == XenoGeneration.OLD || generation == XenoGeneration.SURVIVOR) {
            List<XGenerationalArena> targetArenas = getArenaList(generation);
            if (targetArenas.isEmpty()) {
                this.addNewArena(generation, getDefaultCapacity(generation));
            }

            for (XGenerationalArena arena : targetArenas) {
                if (arena instanceof XenoTlsfArena tlsfArena) {
                    XenoTlsfArena.BlockHeader block = tlsfArena.allocateTlsf(alignedSize);
                    if (block != null) {
                        AllocationHandle handle = new AllocationHandle(this.allocIdCounter.getAndIncrement(), handleId, tlsfArena, block.offset, alignedSize, ownerTag, nowMs, genTag);
                        handle.tlsfBlock = block;
                        updateHandleTable(handleId, genTag, (int) alignedSize, block.offset);
                        this.handleMap.put(handleId, handle);
                        return handle;
                    }
                }
            }

            XGenerationalArena newArena = this.addNewArena(generation, Math.max(getDefaultCapacity(generation), alignedSize));
            if (newArena instanceof XenoTlsfArena tlsfArena) {
                XenoTlsfArena.BlockHeader block = tlsfArena.allocateTlsf(alignedSize);
                if (block != null) {
                    AllocationHandle handle = new AllocationHandle(this.allocIdCounter.getAndIncrement(), handleId, tlsfArena, block.offset, alignedSize, ownerTag, nowMs, genTag);
                    handle.tlsfBlock = block;
                    updateHandleTable(handleId, genTag, (int) alignedSize, block.offset);
                    this.handleMap.put(handleId, handle);
                    return handle;
                }
            }
        }

        List<XGenerationalArena> targetArenas = getArenaList(generation);
        if (targetArenas.isEmpty()) {
            this.addNewArena(generation, getDefaultCapacity(generation));
        }

        XGenerationalArena activeArena = targetArenas.get(targetArenas.size() - 1);
        if (activeArena.canBumpAllocate(alignedSize)) {
            AllocationHandle handle = activeArena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement(), nowMs);
            if (handle != null) {
                updateHandleTable(handleId, genTag, (int) alignedSize, handle.offset);
                this.handleMap.put(handleId, handle);
                return handle;
            }
        }

        for (XGenerationalArena arena : targetArenas) {
            if (arena.canBumpAllocate(alignedSize)) {
                AllocationHandle handle = arena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement(), nowMs);
                if (handle != null) {
                    updateHandleTable(handleId, genTag, (int) alignedSize, handle.offset);
                    this.handleMap.put(handleId, handle);
                    return handle;
                }
            }
        }

        XGenerationalArena newArena = this.addNewArena(generation, Math.max(getDefaultCapacity(generation), alignedSize));
        AllocationHandle handle = newArena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement(), nowMs);
        updateHandleTable(handleId, genTag, (int) alignedSize, handle.offset);
        this.handleMap.put(handleId, handle);
        return handle;
    }

    @Override
    public synchronized void free(AllocationHandle handle) {
        if (handle == null || !handle.valid) return;

        this.freesPerTick.incrementAndGet();
        handle.valid = false;
        this.handleMap.remove(handle.handleId);

        XGenerationalArena arena = handle.arena;
        if (arena != null) {
            if (handle.tlsfBlock != null && arena instanceof XenoTlsfArena tlsfArena) {
                tlsfArena.freeTlsf(handle.tlsfBlock);
            } else {
                arena.removeAllocation(handle);
            }

            if (arena.activeBytes.get() == 0 && arena.generation != XenoGeneration.YOUNG) {
                List<XGenerationalArena> list = getArenaList(arena.generation);
                if (list.size() > 1) {
                    arena.close();
                    list.remove(arena);
                }
            }
        }
    }

    // Phase 3: Adaptive Multi-Strategy Promotion Engine
    public synchronized int tickPromotion(long availableNanos) {
        this.lastAvailableTickNanos = availableNanos;
        this.allocationsPerTick.set(0);
        this.freesPerTick.set(0);

        long nowMs = Util.getMillis();
        long promotionAgeThresholdMs = 5000L; // 5 seconds in Young nursery
        int promotedCount = 0;

        boolean isStressed = availableNanos < 2_000_000L; // < 2ms remaining frame time

        for (XGenerationalArena youngArena : this.youngArenas) {
            synchronized (youngArena.allocations) {
                for (AllocationHandle handle : youngArena.allocations) {
                    if (handle.valid && handle.generationTag == 0 && (nowMs - handle.creationTimeMs > promotionAgeThresholdMs)) {

                        if (isStressed) {
                            // Approach A (Logical Tagging): $O(1)$ enum tag flip without memory movement
                            handle.generationTag = 1; // Mark as Survivor
                            updateHandleTable(handle.handleId, (byte) 1, (int) handle.size, handle.offset);
                            promotedCount++;
                        } else {
                            // Approach B (Physical Segregation): Copy payload using MemorySegment or Direct Mapping
                            if (this.memoryKind == XenoMemoryKind.CPU_OFFHEAP_FFM && youngArena.memorySegment != null) {
                                AllocationHandle survivorHandle = this.allocate(handle.size, handle.ownerTag, XenoGeneration.SURVIVOR);
                                if (survivorHandle != null && survivorHandle.arena != null && survivorHandle.arena.memorySegment != null) {
                                    MemorySegment src = youngArena.memorySegment.asSlice(handle.offset, handle.size);
                                    MemorySegment dest = survivorHandle.arena.memorySegment.asSlice(survivorHandle.offset, survivorHandle.size);
                                    dest.copyFrom(src);

                                    handle.generationTag = 1;
                                    handle.offset = survivorHandle.offset;
                                    handle.arena = survivorHandle.arena;
                                    updateHandleTable(handle.handleId, (byte) 1, (int) handle.size, survivorHandle.offset);
                                    promotedCount++;
                                }
                            } else {
                                // Fallback to logical tagging for GPU VRAM if frame time limit exceeded
                                handle.generationTag = 1;
                                updateHandleTable(handle.handleId, (byte) 1, (int) handle.size, handle.offset);
                                promotedCount++;
                            }
                        }
                    }
                }
            }
        }
        return promotedCount;
    }

    @Override
    public synchronized int tickIncrementalDefrag(int maxMovesPerFrame) {
        return tickPromotion(16_666_667L - System.nanoTime() % 16_666_667L);
    }

    @Override
    public synchronized void reset() {
        for (XGenerationalArena arena : this.youngArenas) arena.close();
        this.youngArenas.clear();
        for (XGenerationalArena arena : this.survivorArenas) arena.close();
        this.survivorArenas.clear();
        for (XGenerationalArena arena : this.oldArenas) arena.close();
        this.oldArenas.clear();
        this.handleMap.clear();

        this.addNewArena(XenoGeneration.YOUNG, this.youngCapacity);
    }

    @Override
    public synchronized void close() {
        for (XGenerationalArena arena : this.youngArenas) arena.close();
        this.youngArenas.clear();
        for (XGenerationalArena arena : this.survivorArenas) arena.close();
        this.survivorArenas.clear();
        for (XGenerationalArena arena : this.oldArenas) arena.close();
        this.oldArenas.clear();
        this.handleMap.clear();

        if (this.tableArena.scope().isAlive()) {
            this.tableArena.close();
        }
    }

    @Override
    public synchronized String getStats() {
        long youngActive = 0L, survivorActive = 0L, oldActive = 0L;
        for (XGenerationalArena arena : this.youngArenas) youngActive += arena.activeBytes.get();
        for (XGenerationalArena arena : this.survivorArenas) survivorActive += arena.activeBytes.get();
        for (XGenerationalArena arena : this.oldArenas) oldActive += arena.activeBytes.get();

        double oldFrag = 0.0;
        if (!this.oldArenas.isEmpty()) {
            for (XGenerationalArena arena : this.oldArenas) {
                oldFrag += arena.getFragmentationRatio();
            }
            oldFrag /= this.oldArenas.size();
        }

        return String.format("[%s] Young: %d (%.1fMB) | Survivor: %d (%.1fMB) | Old: %d (%.1fMB) | Old Frag: %.1f%% | Alloc/Tick: %d | Available: %.2fms",
                this.memoryKind.name(),
                this.youngArenas.size(), youngActive / (1024.0 * 1024.0),
                this.survivorArenas.size(), survivorActive / (1024.0 * 1024.0),
                this.oldArenas.size(), oldActive / (1024.0 * 1024.0),
                oldFrag * 100.0,
                this.allocationsPerTick.get(),
                this.lastAvailableTickNanos / 1_000_000.0);
    }
}
