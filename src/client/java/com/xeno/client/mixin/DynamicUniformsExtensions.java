package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniforms;
import java.util.List;

public interface DynamicUniformsExtensions {
    GpuBufferSlice[] xenoWriteChunkSections(List<DynamicUniforms.ChunkSectionInfo> infos);
}
