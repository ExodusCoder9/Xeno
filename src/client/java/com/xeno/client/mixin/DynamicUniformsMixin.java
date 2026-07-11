package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import java.util.List;

@Mixin(DynamicUniforms.class)
public class DynamicUniformsMixin implements DynamicUniformsExtensions {
    @Shadow @Final private DynamicUniformStorage<DynamicUniforms.ChunkSectionInfo> chunkSections;

    @Override
    @SuppressWarnings("unchecked")
    public GpuBufferSlice[] xenoWriteChunkSections(List<DynamicUniforms.ChunkSectionInfo> infos) {
        return ((DynamicUniformStorageExtensions<DynamicUniforms.ChunkSectionInfo>) this.chunkSections).xenoWriteUniforms(infos);
    }
}
