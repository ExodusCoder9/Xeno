package com.xeno.client.renderer.memory;

/**
 * Interface defining the API contract for the Unified Generational Multi-Buffer Allocator.
 */
public interface IXenoArenaAllocator {

    public interface DefragListener {
        void onAllocationMoved(XGenerationalMultiBufferAllocator.AllocationHandle handle, long oldOffset, long newOffset);
    }

    XGenerationalMultiBufferAllocator.AllocationHandle allocate(long size, Object ownerTag);
    void free(XGenerationalMultiBufferAllocator.AllocationHandle handle);
    int tickIncrementalDefrag(int maxMovesPerFrame);
    void reset();
    void close();
    String getStats();
}
