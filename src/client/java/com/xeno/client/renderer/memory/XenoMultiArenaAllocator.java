package com.xeno.client.renderer.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Incrementally Defragmenting Auto-Sizing Multi-Arena Allocator.
 * Combines fast O(1) bump allocations across dynamic arenas with zero-stutter background defragmentation
 * and O(1) swap-removal deallocations.
 */
public class XenoMultiArenaAllocator implements IXenoArenaAllocator {

    public static class AllocationHandle {
        public final long id;
        public XenoDefragArena arena;
        public long offset;
        public final long size;
        public Object ownerTag;
        public int indexInArena = -1;
        public boolean valid = true;

        public AllocationHandle(long id, XenoDefragArena arena, long offset, long size, Object ownerTag) {
            this.id = id;
            this.arena = arena;
            this.offset = offset;
            this.size = size;
            this.ownerTag = ownerTag;
        }

        public GpuBuffer getBuffer() {
            return this.arena != null ? this.arena.buffer : null;
        }
    }

    private final String namePrefix;
    private final int usage;
    private final long defaultArenaSize;
    private final List<XenoDefragArena> arenas = new ArrayList<>();
    private final AtomicLong allocIdCounter = new AtomicLong(1);

    public XenoMultiArenaAllocator(String namePrefix, int usage, long defaultArenaSize) {
        this.namePrefix = namePrefix;
        this.usage = usage;
        this.defaultArenaSize = defaultArenaSize;
        this.addNewArena(defaultArenaSize);
    }

    private synchronized XenoDefragArena addNewArena(long minSize) {
        long arenaSize = Math.max(this.defaultArenaSize, minSize);
        XenoDefragArena arena = new XenoDefragArena(this.arenas.size(), this.namePrefix, this.usage, arenaSize);
        this.arenas.add(arena);
        return arena;
    }

    @Override
    public synchronized AllocationHandle allocate(long size, Object ownerTag) {
        long alignedSize = (size + 15) & ~15;

        // Try O(1) bump allocation on current active arena
        XenoDefragArena activeArena = this.arenas.get(this.arenas.size() - 1);
        if (activeArena.canBumpAllocate(alignedSize)) {
            return activeArena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement());
        }

        // Try existing arenas if any have space via bump
        for (int i = 0; i < this.arenas.size() - 1; i++) {
            XenoDefragArena arena = this.arenas.get(i);
            if (arena.canBumpAllocate(alignedSize)) {
                return arena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement());
            }
        }

        // Auto-sizing: Create new dynamic arena
        XenoDefragArena newArena = this.addNewArena(alignedSize);
        return newArena.bumpAllocate(alignedSize, ownerTag, this.allocIdCounter.getAndIncrement());
    }

    @Override
    public synchronized void free(AllocationHandle handle) {
        if (handle == null || !handle.valid) return;

        handle.valid = false;
        XenoDefragArena arena = handle.arena;
        if (arena != null) {
            arena.removeAllocation(handle);

            // Auto-shrinking: Destroy non-primary arenas if completely empty
            if (arena.activeBytes == 0 && this.arenas.size() > 1 && arena.arenaId > 0) {
                arena.close();
                this.arenas.remove(arena);
            }
        }
    }

    @Override
    public synchronized int tickIncrementalDefrag(int maxMovesPerFrame) {
        int movedCount = 0;

        for (XenoDefragArena arena : this.arenas) {
            if (movedCount >= maxMovesPerFrame) break;
            if (arena.allocations.size() <= 1 || arena.getFragmentationRatio() < 0.10) continue;

            // Compact allocations backward within the arena
            long currentTargetOffset = 0L;
            for (int i = 0; i < arena.allocations.size(); i++) {
                if (movedCount >= maxMovesPerFrame) break;

                AllocationHandle handle = arena.allocations.get(i);
                if (!handle.valid) continue;

                if (handle.offset > currentTargetOffset) {
                    long oldOffset = handle.offset;
                    long newOffset = currentTargetOffset;

                    // Copy GPU buffer memory slice using FFM SIMD copy
                    try (GpuBufferSlice.MappedView src = arena.buffer.slice(oldOffset, handle.size).map(true, false);
                         GpuBufferSlice.MappedView dst = arena.buffer.slice(newOffset, handle.size).map(false, true)) {
                        MemoryIntrinsics.copy(src.data(), dst.data(), (int) handle.size);
                    } catch (Exception e) {
                        // Silently skip if mapping fails
                    }

                    handle.offset = newOffset;
                    movedCount++;

                    // Notify owner tag if it implements DefragListener
                    if (handle.ownerTag instanceof DefragListener listener) {
                        listener.onAllocationMoved(handle, oldOffset, newOffset);
                    }
                }

                currentTargetOffset += handle.size;
            }

            // Adjust bump offset to reflect compacted space
            arena.bumpOffset = currentTargetOffset;
        }

        return movedCount;
    }

    @Override
    public synchronized void reset() {
        if (this.arenas.size() > 1) {
            for (int i = 1; i < this.arenas.size(); i++) {
                this.arenas.get(i).close();
            }
            XenoDefragArena first = this.arenas.get(0);
            this.arenas.clear();
            this.arenas.add(first);
        }

        XenoDefragArena first = this.arenas.get(0);
        first.allocations.clear();
        first.bumpOffset = 0L;
        first.activeBytes = 0L;
    }

    @Override
    public synchronized void close() {
        for (XenoDefragArena arena : this.arenas) {
            arena.close();
        }
        this.arenas.clear();
    }

    @Override
    public synchronized String getStats() {
        long totalCapacity = 0L;
        long totalActive = 0L;
        for (XenoDefragArena arena : this.arenas) {
            totalCapacity += arena.capacity;
            totalActive += arena.activeBytes;
        }
        return String.format("Arenas: %d | Capacity: %.2f MB | Active: %.2f MB",
                this.arenas.size(), totalCapacity / (1024.0 * 1024.0), totalActive / (1024.0 * 1024.0));
    }
}
