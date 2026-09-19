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

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.xeno.client.common.render.chunk.XenoVertexFormats;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.resources.Identifier;

public final class XenoRenderPipelines {
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("xeno", "core/terrain");
    private static final Identifier FRAGMENT_SHADER = Identifier.withDefaultNamespace("core/terrain");

    public static final RenderPipeline.Snippet XENO_TERRAIN_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION)
            .withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO)
            .withVertexBinding(0, XenoVertexFormats.TERRAIN)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withVertexShader(VERTEX_SHADER)
            .withFragmentShader(FRAGMENT_SHADER)
            .buildSnippet();

    public static final RenderPipeline.Snippet XENO_MULTIDRAW_TERRAIN_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO)
            .withVertexBinding(0, XenoVertexFormats.TERRAIN)
            .withVertexBinding(1, DefaultVertexFormat.CHUNK_DATA_INSTANCED)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withShaderDefine("MULTIDRAW_TERRAIN")
            .withVertexShader(VERTEX_SHADER)
            .withFragmentShader(FRAGMENT_SHADER)
            .buildSnippet();

    public static final RenderPipeline SOLID_TERRAIN = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/solid_terrain"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline CUTOUT_TERRAIN = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/cutout_terrain"))
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline TRANSLUCENT_TERRAIN = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/translucent_terrain"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .build();

    public static final RenderPipeline WIREFRAME = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/wireframe"))
            .withPolygonMode(PolygonMode.WIREFRAME)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline SOLID_TERRAIN_MULTIDRAW = RenderPipeline.builder(XENO_MULTIDRAW_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/solid_terrain_multidraw"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline CUTOUT_TERRAIN_MULTIDRAW = RenderPipeline.builder(XENO_MULTIDRAW_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/cutout_terrain_multidraw"))
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final RenderPipeline TRANSLUCENT_TERRAIN_MULTIDRAW = RenderPipeline.builder(XENO_MULTIDRAW_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/translucent_terrain_multidraw"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .build();

    public static final RenderPipeline WIREFRAME_MULTIDRAW = RenderPipeline.builder(XENO_MULTIDRAW_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/wireframe_multidraw"))
            .withPolygonMode(PolygonMode.WIREFRAME)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    public static final OitPipelineSet OIT_TERRAIN = OitPipelineSet.builder(
            "xeno_terrain",
            RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        )
        .withAccumulateModifier(accumulate -> accumulate.withBindGroupLayout(BindGroupLayouts.SAMPLER2))
        .build();

    public static final OitPipelineSet OIT_TERRAIN_MULTIDRAW = OitPipelineSet.builder(
            "xeno_terrain_multidraw",
            RenderPipeline.builder(XENO_MULTIDRAW_TERRAIN_SNIPPET)
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        )
        .withAccumulateModifier(accumulate -> accumulate.withBindGroupLayout(BindGroupLayouts.SAMPLER2))
        .build();

    public static RenderPipeline getPipeline(ChunkSectionLayer layer, boolean wireframe) {
        return getPipeline(layer, wireframe, false);
    }

    public static RenderPipeline getPipeline(ChunkSectionLayer layer, boolean wireframe, boolean multiDraw) {
        if (wireframe) {
            return multiDraw ? WIREFRAME_MULTIDRAW : WIREFRAME;
        }
        return switch (layer) {
            case SOLID -> multiDraw ? SOLID_TERRAIN_MULTIDRAW : SOLID_TERRAIN;
            case CUTOUT -> multiDraw ? CUTOUT_TERRAIN_MULTIDRAW : CUTOUT_TERRAIN;
            case TRANSLUCENT -> multiDraw ? TRANSLUCENT_TERRAIN_MULTIDRAW : TRANSLUCENT_TERRAIN;
        };
    }

    public static RenderPipeline getOitPipeline(OitStage stage, boolean multiDraw) {
        return multiDraw ? OIT_TERRAIN_MULTIDRAW.getPipeline(stage) : OIT_TERRAIN.getPipeline(stage);
    }

    private XenoRenderPipelines() {
    }
}
