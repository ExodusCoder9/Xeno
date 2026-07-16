package com.xeno.client.util;

import com.xeno.client.renderer.XenoBufferPool;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public interface XenoMeshExtension {
    void xeno$setAllocations(ChunkSectionLayer layer, XenoBufferPool.Allocation vertexAlloc, XenoBufferPool.Allocation indexAlloc);
    XenoBufferPool.Allocation xeno$getVertexAllocation(ChunkSectionLayer layer);
    XenoBufferPool.Allocation xeno$getIndexAllocation(ChunkSectionLayer layer);
    void xeno$clearBuffers();
}
