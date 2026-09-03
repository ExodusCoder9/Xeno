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

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.PolygonMode;
import com.xeno.client.common.render.chunk.XenoVertexFormats;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
            .withVertexBinding(0, XenoVertexFormats.TERRAIN)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withVertexShader(VERTEX_SHADER)
            .withFragmentShader(FRAGMENT_SHADER)
            .buildSnippet();

    public static final RenderPipeline SOLID_TERRAIN = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/solid_terrain"))
            .build();

    public static final RenderPipeline CUTOUT_TERRAIN = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/cutout_terrain"))
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .build();

    public static final RenderPipeline TRANSLUCENT_TERRAIN = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/translucent_terrain"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .build();

    public static final RenderPipeline WIREFRAME = RenderPipeline.builder(XENO_TERRAIN_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("xeno", "pipeline/wireframe"))
            .withPolygonMode(PolygonMode.WIREFRAME)
            .build();

    public static RenderPipeline getPipeline(ChunkSectionLayer layer, boolean wireframe) {
        if (wireframe) {
            return WIREFRAME;
        }
        return switch (layer) {
            case SOLID -> SOLID_TERRAIN;
            case CUTOUT -> CUTOUT_TERRAIN;
            case TRANSLUCENT -> TRANSLUCENT_TERRAIN;
        };
    }

    private XenoRenderPipelines() {
    }
}
