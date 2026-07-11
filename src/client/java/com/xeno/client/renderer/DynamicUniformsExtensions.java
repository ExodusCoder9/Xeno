package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniforms;
import java.util.List;

public interface DynamicUniformsExtensions {
    GpuBufferSlice[] xeno$writeChunkSections(List<DynamicUniforms.ChunkSectionInfo> infos);
}
