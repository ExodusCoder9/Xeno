package com.xeno.client.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public interface XenoMeshExtension {
    void xeno$setBuffers(ChunkSectionLayer layer, GpuBuffer vertexBuffer, GpuBuffer indexBuffer);
    GpuBuffer xeno$getVertexBuffer(ChunkSectionLayer layer);
    GpuBuffer xeno$getIndexBuffer(ChunkSectionLayer layer);
    void xeno$clearBuffers();
}
