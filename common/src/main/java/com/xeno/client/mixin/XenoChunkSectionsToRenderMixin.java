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

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.xeno.client.common.render.XenoChunkRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkSectionsToRender.class)
public abstract class XenoChunkSectionsToRenderMixin {
	@Shadow @Final private int maxIndicesRequired;
	@Shadow @Final private GpuBufferSlice terrainTransformUBO;

	@Shadow
	protected abstract void render(
		final ChunkSectionLayer layer,
		final RenderPass renderPass,
		final @Nullable GpuBuffer defaultIndexBuffer,
		final @Nullable IndexType defaultIndexType,
		final @Nullable RenderPipeline renderPipelineOverride,
		final @Nullable RenderPipeline renderPipelineOverrideMultidraw
	);

	@org.spongepowered.asm.mixin.Unique
	private final XenoChunkRenderer.RenderInvoker xeno$renderInvoker = this::render;

	/**
	 * @author ExodusCoder9
	 * @reason Delegate the chunk layer group rendering to XenoChunkRenderer
	 */
	@Overwrite
	public void renderGroup(
		final ChunkSectionLayerGroup group,
		final RenderPass renderPass,
		final GpuSampler sampler,
		final GpuTextureView atlas,
		final boolean renderWireframeTerrain
	) {
		XenoChunkRenderer.INSTANCE.renderChunks(
			(ChunkSectionsToRender) (Object) this,
			group,
			renderPass,
			sampler,
			atlas,
			renderWireframeTerrain,
			this.maxIndicesRequired,
			this.terrainTransformUBO,
			this.xeno$renderInvoker
		);
	}

	/**
	 * @author ExodusCoder9
	 * @reason Delegate the OIT chunk rendering to XenoChunkRenderer
	 */
	@Overwrite
	public void renderOit(
		final GpuSampler sampler,
		final OitStage stage,
		final OitRenderPassProvider.Parameters params,
		final GpuTextureView atlas,
		final GpuTextureView lightmap
	) {
		XenoChunkRenderer.INSTANCE.renderOit(
			(ChunkSectionsToRender) (Object) this,
			sampler,
			stage,
			params,
			atlas,
			lightmap,
			this.maxIndicesRequired,
			this.terrainTransformUBO,
			this.xeno$renderInvoker
		);
	}
}
