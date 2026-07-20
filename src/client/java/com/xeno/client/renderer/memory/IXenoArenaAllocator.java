package com.xeno.client.renderer.memory;

/**
 * Interface defining the API contract for the Incrementally Defragmenting Auto-Sizing Multi-Arena Allocator.
 */
public interface IXenoArenaAllocator {

    public interface DefragListener {
        void onAllocationMoved(XenoMultiArenaAllocator.AllocationHandle handle, long oldOffset, long newOffset);
    }

    XenoMultiArenaAllocator.AllocationHandle allocate(long size, Object ownerTag);
    void free(XenoMultiArenaAllocator.AllocationHandle handle);
    int tickIncrementalDefrag(int maxMovesPerFrame);
    void reset();
    void close();
    String getStats();
}
