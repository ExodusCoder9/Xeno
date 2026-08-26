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

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.xeno.client.common.render.chunk.XenoSharedQuadIndexBuffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

/**
 * Owns the actual GPU draw-call submission for terrain.
 *<p>
 *
 * This class opens the render pass itself, binds its own textures/samplers and pipelines, and submits the section draws in
 * one drawMultipleIndexed batch per layer.
 */
public final class XenoChunkRenderer {
	public static final XenoChunkRenderer INSTANCE = new XenoChunkRenderer();

	private static final List<String> DYNAMIC_CHUNK_SECTION_UNIFORMS = List.of("ChunkSection");

	public static final XenoSharedQuadIndexBuffer SHARED_INDEX_BUFFER = new XenoSharedQuadIndexBuffer();

	private XenoChunkRenderer() {
	}

	private final List<RenderPass.Draw<GpuBufferSlice[]>> layerDrawsScratch = new ArrayList<>();

	/**
	 * Submits every draw in chunkRenders for the given layer group directly to the GPU.
	 * Must be called on the render thread. We get the render target  from the layer group the
	 * same way vanilla does (main framebuffer for opaque layers, the translucent target otherwise),
	 * so the depth/color attachments already contain everything the pass needs.
	 */
	public void renderChunks(ChunkSectionsToRender chunkRenders, ChunkSectionLayerGroup group, GpuSampler sampler) {
		GpuTextureView blockAtlas = chunkRenders.textureView();
		int maxIndicesRequired = chunkRenders.maxIndicesRequired();

		XenoSharedQuadIndexBuffer sharedIndices = SHARED_INDEX_BUFFER;
		sharedIndices.ensureCapacity(maxIndicesRequired);
		GpuBuffer defaultIndexBuffer = maxIndicesRequired == 0 || !sharedIndices.hasCapacity(maxIndicesRequired)
			? null
			: sharedIndices.buffer();
		IndexType defaultIndexType = defaultIndexBuffer == null ? null : sharedIndices.type();

		ChunkSectionLayer[] layers = group.layers();
		Minecraft minecraft = Minecraft.getInstance();
		boolean wireframe = SharedConstants.DEBUG_HOTKEYS && minecraft.wireframe;
		RenderTarget renderTarget = group.outputTarget();
		GpuTextureView colorView = renderTarget.getColorTextureView();
		GpuTextureView depthView = renderTarget.getDepthTextureView();
		if (colorView == null || depthView == null) {
			throw new IllegalStateException("Render target " + renderTarget + " has no color/depth attachments");
		}

		try (RenderPass renderPass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				() -> "Xeno section layers for " + group.label(),
				colorView,
				Optional.empty(),
				depthView,
				OptionalDouble.empty()
			)) {
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.bindTexture("Sampler0", blockAtlas, sampler);
			renderPass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

			GpuBufferSlice[] chunkSectionInfos = chunkRenders.chunkSectionInfos();
			for (ChunkSectionLayer layer : layers) {
				renderPass.setPipeline(wireframe ? RenderPipelines.WIREFRAME : layer.pipeline());
				Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup = chunkRenders.drawGroupsPerLayer().get(layer);
				if (drawGroup == null) {
					continue;
				}

				this.layerDrawsScratch.clear();
				ObjectIterator<List<RenderPass.Draw<GpuBufferSlice[]>>> iterator = drawGroup.values().iterator();
				while (iterator.hasNext()) {
					List<RenderPass.Draw<GpuBufferSlice[]>> draws = iterator.next();
					if (draws.isEmpty()) {
						continue;
					}

					if (layer == ChunkSectionLayer.TRANSLUCENT) {
						for (int i = draws.size() - 1; i >= 0; i--) {
							this.layerDrawsScratch.add(draws.get(i));
						}
					} else {
						this.layerDrawsScratch.addAll(draws);
					}
				}

				if (!this.layerDrawsScratch.isEmpty()) {
					renderPass.drawMultipleIndexed(this.layerDrawsScratch, defaultIndexBuffer, defaultIndexType, DYNAMIC_CHUNK_SECTION_UNIFORMS, chunkSectionInfos);
				}
			}
		}
	}
}
