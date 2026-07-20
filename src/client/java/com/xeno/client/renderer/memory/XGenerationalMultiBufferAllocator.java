package com.xeno.client.renderer.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.util.Util;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-generational arena allocator supporting both GPU VRAM and CPU off-heap memory.
 * Organizes allocations into Young, Survivor, and Old generation tiers to isolate volatile
 * mesh churn from long-lived terrain structures.
 * Features lock-free read paths for sub-microsecond frame stability.
 */
public class XGenerationalMultiBufferAllocator implements IXenoArenaAllocator {

    public static class AllocationHandle {
        public final long id;
        public XGenerationalArena arena;
        public long offset;
        public final long size;
        public Object ownerTag;
        public final long creationTimeMs;
        public int indexInArena = -1;
        public boolean valid = true;

        public AllocationHandle(long id, XGenerationalArena arena, long offset, long size, Object ownerTag, long creationTimeMs) {
            this.id = id;
            this.arena = arena;
            this.offset = offset;
            this.size = size;
            this.ownerTag = ownerTag;
            this.creationTimeMs = creationTimeMs;
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
            return this.arena != null ? this.arena.generation : XenoGeneration.YOUNG;
        }

        public XenoMemoryKind getMemoryKind() {
            return this.arena != null ? this.arena.memoryKind : XenoMemoryKind.GPU_VRAM;
        }
    }

    private final String namePrefix;
    private final XenoMemoryKind memoryKind;
    private final int gpuUsageFlags;
    private final long youngCapacity;
    private final long survivorCapacity;
    private final long oldCapacity;

    // CopyOnWrite lists provide 100% lock-free read paths for containsBuffer during rendering
    private final List<XGenerationalArena> youngArenas = new CopyOnWriteArrayList<>();
    private final List<XGenerationalArena> survivorArenas = new CopyOnWriteArrayList<>();
    private final List<XGenerationalArena> oldArenas = new CopyOnWriteArrayList<>();
    private final AtomicLong allocIdCounter = new AtomicLong(1);

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
        XGenerationalArena arena = new XGenerationalArena(list.size(), gen, this.memoryKind, this.namePrefix, this.gpuUsageFlags, capacity);
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

    /**
     * 100% Lock-Free buffer existence check to ensure zero main-thread render stalls.
     */
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
        long alignedSize = (size + 15) & ~15;
        long nowMs = Util.getMillis();
        List<XGenerationalArena> targetArenas = getArenaList(generation);

        if (targetArenas.isEmpty()) {
            this.addNewArena(generation, getDefaultCapacity(generation));
        }

        // Try bump allocation in current active arena
        XGenerationalArena activeArena = targetArenas.get(targetArenas.size() - 1);
        if (activeArena.canBumpAllocate(alignedSize)) {
            return activeArena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement(), nowMs);
        }

        // Check existing arenas for available bump space
        for (XGenerationalArena arena : targetArenas) {
            if (arena.canBumpAllocate(alignedSize)) {
                return arena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement(), nowMs);
            }
        }

        // Dynamically create a new arena when capacity is exceeded
        long cap = getDefaultCapacity(generation);
        XGenerationalArena newArena = this.addNewArena(generation, Math.max(cap, alignedSize));
        return newArena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement(), nowMs);
    }

    @Override
    public synchronized void free(AllocationHandle handle) {
        if (handle == null || !handle.valid) return;

        handle.valid = false;
        XGenerationalArena arena = handle.arena;
        if (arena != null) {
            arena.removeAllocation(handle);

            // Reclaim empty non-primary Old or Survivor arenas
            if (arena.activeBytes == 0 && arena.generation != XenoGeneration.YOUNG) {
                List<XGenerationalArena> list = getArenaList(arena.generation);
                if (list.size() > 1) {
                    arena.close();
                    list.remove(arena);
                }
            }
        }
    }

    @Override
    public synchronized int tickIncrementalDefrag(int maxMovesPerFrame) {
        // Space recovery is performed automatically during deallocation resets
        return 0;
    }

    @Override
    public synchronized void reset() {
        for (XGenerationalArena arena : this.youngArenas) arena.close();
        this.youngArenas.clear();
        for (XGenerationalArena arena : this.survivorArenas) arena.close();
        this.survivorArenas.clear();
        for (XGenerationalArena arena : this.oldArenas) arena.close();
        this.oldArenas.clear();

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
    }

    @Override
    public synchronized String getStats() {
        long youngActive = 0L, survivorActive = 0L, oldActive = 0L;
        for (XGenerationalArena arena : this.youngArenas) youngActive += arena.activeBytes;
        for (XGenerationalArena arena : this.survivorArenas) survivorActive += arena.activeBytes;
        for (XGenerationalArena arena : this.oldArenas) oldActive += arena.activeBytes;

        return String.format("[%s] Young: %d (%.1fMB) | Survivor: %d (%.1fMB) | Old: %d (%.1fMB)",
                this.memoryKind.name(),
                this.youngArenas.size(), youngActive / (1024.0 * 1024.0),
                this.survivorArenas.size(), survivorActive / (1024.0 * 1024.0),
                this.oldArenas.size(), oldActive / (1024.0 * 1024.0));
    }
}
