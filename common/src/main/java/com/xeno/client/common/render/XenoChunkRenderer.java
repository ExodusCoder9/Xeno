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

package com.xeno.client.common.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.xeno.client.common.render.chunk.XenoSharedQuadIndexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import org.jspecify.annotations.Nullable;

/**
 * Owns GPU draw-call submission for terrain in Xeno.
 * Coordinates shared index buffers, bindings, and MultiDrawIndirect pipeline execution.
 */
public final class XenoChunkRenderer {
    public static final XenoChunkRenderer INSTANCE = new XenoChunkRenderer();
    public static final XenoSharedQuadIndexBuffer SHARED_INDEX_BUFFER = new XenoSharedQuadIndexBuffer();

    @FunctionalInterface
    public interface RenderInvoker {
        void render(
            ChunkSectionLayer layer,
            RenderPass renderPass,
            @Nullable GpuBuffer defaultIndexBuffer,
            @Nullable IndexType defaultIndexType,
            @Nullable RenderPipeline renderPipelineOverride,
            @Nullable RenderPipeline renderPipelineOverrideMultidraw
        );
    }

    private static final java.util.function.Supplier<String>[] LAYER_DEBUG_GROUPS;
    private static final java.util.function.Supplier<String>[][] OIT_DEBUG_GROUPS;
    private static final java.util.function.Supplier<String>[] OIT_PASS_NAMES;

    static {
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        LAYER_DEBUG_GROUPS = new java.util.function.Supplier[layers.length];
        for (int i = 0; i < layers.length; i++) {
            String label = "Xeno terrain layer: " + layers[i].label();
            LAYER_DEBUG_GROUPS[i] = () -> label;
        }

        OitStage[] stages = OitStage.values();
        OIT_DEBUG_GROUPS = new java.util.function.Supplier[stages.length][layers.length];
        OIT_PASS_NAMES = new java.util.function.Supplier[stages.length];
        for (int s = 0; s < stages.length; s++) {
            String passLabel = "Xeno terrain OIT: " + stages[s].name();
            OIT_PASS_NAMES[s] = () -> passLabel;
            for (int l = 0; l < layers.length; l++) {
                String label = "Xeno OIT terrain layer: " + layers[l].label() + " (" + stages[s].name() + ")";
                OIT_DEBUG_GROUPS[s][l] = () -> label;
            }
        }
    }

    private XenoChunkRenderer() {
    }

    public void renderChunks(
        ChunkSectionLayerGroup group,
        RenderPass renderPass,
        GpuSampler sampler,
        GpuTextureView atlas,
        boolean renderWireframeTerrain,
        int maxIndicesRequired,
        GpuBufferSlice terrainTransformUBO,
        RenderInvoker renderInvoker
    ) {
        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
        GpuTextureView lightmap = gameRenderer.lightmap();

        SHARED_INDEX_BUFFER.ensureCapacity(maxIndicesRequired);
        GpuBuffer defaultIndexBuffer = maxIndicesRequired == 0 || SHARED_INDEX_BUFFER.hasCapacity(maxIndicesRequired)
            ? null
            : SHARED_INDEX_BUFFER.buffer();
        IndexType defaultIndexType = defaultIndexBuffer == null ? null : SHARED_INDEX_BUFFER.type();

        renderPass.setUniform("TerrainUniform", terrainTransformUBO);
        renderPass.setUniform("Sampler0", atlas, sampler);
        renderPass.setUniform("Sampler2", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        for (ChunkSectionLayer layer : group.layers()) {
            renderPass.pushDebugGroup(LAYER_DEBUG_GROUPS[layer.ordinal()]);
            RenderPipeline normalPipeline = XenoRenderPipelines.getPipeline(layer, renderWireframeTerrain, false);
            RenderPipeline multiDrawPipeline = XenoRenderPipelines.getPipeline(layer, renderWireframeTerrain, true);
            renderInvoker.render(layer, renderPass, defaultIndexBuffer, defaultIndexType, normalPipeline, multiDrawPipeline);
            renderPass.popDebugGroup();
        }
    }

    public void renderOit(
        GpuSampler sampler,
        OitStage stage,
        OitRenderPassProvider.Parameters params,
        GpuTextureView atlas,
        GpuTextureView lightmap,
        int maxIndicesRequired,
        GpuBufferSlice terrainTransformUBO,
        RenderInvoker renderInvoker
    ) {
        SHARED_INDEX_BUFFER.ensureCapacity(maxIndicesRequired);
        GpuBuffer defaultIndexBuffer = maxIndicesRequired == 0 || SHARED_INDEX_BUFFER.hasCapacity(maxIndicesRequired)
            ? null
            : SHARED_INDEX_BUFFER.buffer();
        IndexType defaultIndexType = defaultIndexBuffer == null ? null : SHARED_INDEX_BUFFER.type();

        try (RenderPass renderPass = OitRenderPassProvider.createRenderPass(stage, OIT_PASS_NAMES[stage.ordinal()], params)) {
            renderPass.setUniform("TerrainUniform", terrainTransformUBO);
            renderPass.setUniform("Sampler0", atlas, sampler);
            renderPass.setUniform("Sampler2", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

            RenderPipeline normalPipeline = XenoRenderPipelines.getOitPipeline(stage, false);
            RenderPipeline multiDrawPipeline = XenoRenderPipelines.getOitPipeline(stage, true);

            int stageOrdinal = stage.ordinal();
            for (ChunkSectionLayer layer : ChunkSectionLayerGroup.TRANSLUCENT.layers()) {
                renderPass.pushDebugGroup(OIT_DEBUG_GROUPS[stageOrdinal][layer.ordinal()]);
                renderInvoker.render(layer, renderPass, defaultIndexBuffer, defaultIndexType, normalPipeline, multiDrawPipeline);
                renderPass.popDebugGroup();
            }
        }
    }
}
