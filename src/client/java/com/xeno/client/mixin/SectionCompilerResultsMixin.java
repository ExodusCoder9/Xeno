package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionCompiler.Results.class)
public class SectionCompilerResultsMixin {
    @Unique
    public Map<ChunkSectionLayer, MeshData> xeno$fluidLayers = new EnumMap<>(ChunkSectionLayer.class);
}
