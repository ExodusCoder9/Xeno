package com.xeno.client.renderer.util;

import com.xeno.client.renderer.XenoBufferPool;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public interface XenoMeshExtension {
    void xeno$setAllocations(ChunkSectionLayer layer, XenoBufferPool.Allocation vertexAlloc, XenoBufferPool.Allocation indexAlloc);
    XenoBufferPool.Allocation xeno$getVertexAllocation(ChunkSectionLayer layer);
    XenoBufferPool.Allocation xeno$getIndexAllocation(ChunkSectionLayer layer);
    void xeno$clearBuffers();
    void xeno$setTranslucentData(float[] quadCenters, int quadCount);
    float[] xeno$getTranslucentQuadCenters();
    int xeno$getTranslucentQuadCount();
}
