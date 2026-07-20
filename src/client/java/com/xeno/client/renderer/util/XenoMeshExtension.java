package com.xeno.client.renderer.util;

import com.xeno.client.renderer.memory.XenoMultiArenaAllocator;
import com.xeno.client.renderer.draw.XenoUniformBinder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public interface XenoMeshExtension {
    void xeno$setAllocations(ChunkSectionLayer layer, XenoMultiArenaAllocator.AllocationHandle vertexAlloc, XenoMultiArenaAllocator.AllocationHandle indexAlloc);
    XenoMultiArenaAllocator.AllocationHandle xeno$getVertexAllocation(ChunkSectionLayer layer);
    XenoMultiArenaAllocator.AllocationHandle xeno$getIndexAllocation(ChunkSectionLayer layer);
    void xeno$clearBuffers();
    void xeno$setTranslucentData(float[] quadCenters, int quadCount);
    float[] xeno$getTranslucentQuadCenters();
    int xeno$getTranslucentQuadCount();

    // Cached draw and binder support
    void xeno$setCachedDraw(ChunkSectionLayer layer, RenderPass.Draw<GpuBufferSlice[]> draw);
    RenderPass.Draw<GpuBufferSlice[]> xeno$getCachedDraw(ChunkSectionLayer layer);
    void xeno$setUniformBinder(ChunkSectionLayer layer, XenoUniformBinder binder);
    XenoUniformBinder xeno$getUniformBinder(ChunkSectionLayer layer);
}
