package com.xeno.client.renderer.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;

/**
 * Single GPU Buffer Arena within the Multi-Arena Allocator system.
 * Supports fast O(1) bump allocation and O(1) swap-removal deallocation.
 */
public class XenoDefragArena {
    public final int arenaId;
    public final GpuBuffer buffer;
    public final long capacity;
    public final List<XenoMultiArenaAllocator.AllocationHandle> allocations = new ArrayList<>();

    public long bumpOffset = 0L;
    public long activeBytes = 0L;

    public XenoDefragArena(int arenaId, String namePrefix, int usage, long capacity) {
        this.arenaId = arenaId;
        this.capacity = capacity;
        this.buffer = RenderSystem.getDevice().createBuffer(
                () -> namePrefix + "-Arena-" + arenaId,
                usage,
                capacity
        );
    }

    public boolean canBumpAllocate(long alignedSize) {
        return this.bumpOffset + alignedSize <= this.capacity;
    }

    public XenoMultiArenaAllocator.AllocationHandle bumpAllocate(long alignedSize, Object ownerTag, long allocId) {
        long offset = this.bumpOffset;
        this.bumpOffset += alignedSize;
        this.activeBytes += alignedSize;

        XenoMultiArenaAllocator.AllocationHandle handle =
                new XenoMultiArenaAllocator.AllocationHandle(allocId, this, offset, alignedSize, ownerTag);
        handle.indexInArena = this.allocations.size();
        this.allocations.add(handle);
        return handle;
    }

    /**
     * Fast O(1) swap-removal of deallocated handles.
     */
    public void removeAllocation(XenoMultiArenaAllocator.AllocationHandle handle) {
        int idx = handle.indexInArena;
        int lastIdx = this.allocations.size() - 1;

        if (idx >= 0 && idx <= lastIdx && this.allocations.get(idx) == handle) {
            if (idx != lastIdx) {
                XenoMultiArenaAllocator.AllocationHandle lastHandle = this.allocations.get(lastIdx);
                this.allocations.set(idx, lastHandle);
                lastHandle.indexInArena = idx;
            }
            this.allocations.remove(lastIdx);
            this.activeBytes -= handle.size;
            handle.indexInArena = -1;
        }
    }

    public double getFragmentationRatio() {
        if (this.bumpOffset == 0) return 0.0;
        long holeBytes = this.bumpOffset - this.activeBytes;
        return (double) holeBytes / (double) this.bumpOffset;
    }

    public void close() {
        if (this.buffer != null && !this.buffer.isClosed()) {
            this.buffer.close();
        }
        this.allocations.clear();
        this.bumpOffset = 0L;
        this.activeBytes = 0L;
    }
}
