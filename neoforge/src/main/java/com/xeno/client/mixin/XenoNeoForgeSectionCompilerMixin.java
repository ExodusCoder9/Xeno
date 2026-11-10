/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.xeno.client.common.render.chunk.XenoSectionCompiler;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mixin(SectionCompiler.class)
public abstract class XenoNeoForgeSectionCompilerMixin {
    @Final
    @Shadow private boolean ambientOcclusion;
    @Final
    @Shadow private net.minecraft.client.color.block.BlockColors blockColors;

    @Inject(
        method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void xeno$compileWithNeoForgeSupport(
        SectionPos sectionPos,
        RenderSectionRegion region,
        VertexSorting vertexSorting,
        SectionBufferBuilderPack builders,
        List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
        CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        if (!((Object) this instanceof XenoSectionCompiler xenoCompiler)) {
            return;
        }

        SectionCompiler.Results results = xenoCompiler.compile(sectionPos, region, vertexSorting, builders);

        if (additionalRenderers != null && !additionalRenderers.isEmpty()) {
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
            Map<ChunkSectionLayer, BufferBuilder> startedLayers = new EnumMap<>(ChunkSectionLayer.class);

            ClientHooks.addAdditionalGeometry(
                additionalRenderers,
                layer -> {
                    BufferBuilder builder = startedLayers.get(layer);
                    if (builder == null) {
                        ByteBufferBuilder buffer = builders.buffer(layer);
                        builder = new BufferBuilder(buffer, PrimitiveTopology.QUADS, layer.vertexFormat());
                        startedLayers.put(layer, builder);
                    }
                    return builder;
                },
                region,
                blockRenderer
            );

            for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : startedLayers.entrySet()) {
                ChunkSectionLayer layer = entry.getKey();
                MeshData mesh = entry.getValue().build();
                if (mesh != null) {
                    if (layer == ChunkSectionLayer.TRANSLUCENT) {
                        results.transparencyState = mesh.sortQuads(builders.buffer(layer), vertexSorting);
                    }
                    results.renderedLayers.put(layer, mesh);
                }
            }
        }

        cir.setReturnValue(results);
    }
}
