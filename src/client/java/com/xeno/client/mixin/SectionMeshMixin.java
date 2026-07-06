package com.xeno.client.mixin;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionMesh.class)
public interface SectionMeshMixin {
    @Unique
    default @Nullable SectionMesh.SectionDraw xeno$getFluidSectionDraw(ChunkSectionLayer layer) {
        return null;
    }
}
