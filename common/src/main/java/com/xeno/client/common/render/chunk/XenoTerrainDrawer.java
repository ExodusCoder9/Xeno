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

package com.xeno.client.common.render.chunk;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import org.joml.Matrix4fc;

public final class XenoTerrainDrawer {
	public static final XenoTerrainDrawer INSTANCE = new XenoTerrainDrawer();
	public static final XenoSharedQuadIndexBuffer SHARED_INDEX_BUFFER = new XenoSharedQuadIndexBuffer();
	private static final int SELF_HEAL_MARKS_PER_FRAME = 1024;
	private final it.unimi.dsi.fastutil.longs.LongArrayList missingScratch = new it.unimi.dsi.fastutil.longs.LongArrayList();

	private XenoTerrainDrawer() {
	}

	public ChunkSectionsToRender prepareChunkRenders(LevelRenderer renderer, Matrix4fc modelViewMatrix) {
		Minecraft minecraft = Minecraft.getInstance();

		EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups =
				new EnumMap<>(ChunkSectionLayer.class);
		for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
			drawGroups.put(layer, new Int2ObjectOpenHashMap<>());
		}

		List<DynamicUniforms.ChunkSectionInfo> sectionInfos = new ArrayList<>();
		int[] largestIndexCount = new int[1];
		GpuTextureView blockAtlasView = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		int atlasWidth = blockAtlasView.getWidth(0);
		int atlasHeight = blockAtlasView.getHeight(0);
		long now = Util.getMillis();
		XenoRegionCompiler compiler = XenoRegionCompiler.INSTANCE;
		// Push everything workers staged since the last flush into the GPU heaps before the draw
		// lists below read allocation offsets, otherwise freshly compiled sections get drawn for a
		// frame from memory that has not been copied yet.
		compiler.flushUploads();
		compiler.lock();

		try {
			for (SectionRenderDispatcher.RenderSection visible : renderer.visibleSections) {
				long node = visible.getSectionNode();
				XenoWorldRenderManager.ResolvedSection resolved = XenoWorldRenderManager.INSTANCE.resolveSection(node);
				if (resolved == null) {
					this.missingScratch.add(node);
					continue;
				}

				SectionMesh mesh = resolved.region().meshSlot(resolved.localIndex()).get();
				if (mesh == null || mesh == CompiledSectionMesh.UNCOMPILED) {
					// Only nudge sections that have neither a mesh nor a queued compile;
					// re-marking pending ones every frame flooded the queue with duplicates.
					if (!resolved.region().isPending(resolved.localIndex())) {
						this.missingScratch.add(node);
					}
					continue;
				}

				if (!(mesh instanceof CompiledSectionMesh compiled) || !compiled.hasRenderableLayers()) {
					continue;
				}

				float visibility = resolved.region().visibilityAt(resolved.localIndex(), now);
				appendSectionDraws(
						drawGroups, sectionInfos, largestIndexCount, compiler, compiled, visibility,
						SectionPos.sectionToBlockCoord(SectionPos.x(node)),
						SectionPos.sectionToBlockCoord(SectionPos.y(node)),
						SectionPos.sectionToBlockCoord(SectionPos.z(node)),
						atlasWidth, atlasHeight, modelViewMatrix
				);
			}
		} finally {
			compiler.unlock();
		}

		int healBudget = SELF_HEAL_MARKS_PER_FRAME;
		for (int i = 0; i < this.missingScratch.size() && healBudget > 0; i++, healBudget--) {
			long node = this.missingScratch.getLong(i);
			XenoWorldRenderManager.INSTANCE.onSectionDirty(SectionPos.x(node), SectionPos.y(node), SectionPos.z(node), false);
		}

		this.missingScratch.clear();

		SHARED_INDEX_BUFFER.ensureCapacity(Math.max(largestIndexCount[0], 1));
		var uniformSlices = RenderSystem.getDynamicUniforms()
				.writeChunkSections(sectionInfos.toArray(new DynamicUniforms.ChunkSectionInfo[0]));
		return new ChunkSectionsToRender(blockAtlasView, drawGroups, largestIndexCount[0], uniformSlices);
	}

	private static void appendSectionDraws(
			EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups,
			List<DynamicUniforms.ChunkSectionInfo> sectionInfos,
			int[] largestIndexCount,
			XenoRegionCompiler compiler,
			CompiledSectionMesh compiled,
			float visibility,
			int originX,
			int originY,
			int originZ,
			int atlasWidth,
			int atlasHeight,
			Matrix4fc modelViewMatrix
	) {
		int uboIndex = -1;

		for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
			SectionMesh.SectionDraw draw = compiled.getSectionDraw(layer);
			if (draw == null || !compiled.isVertexBufferUploaded(layer)) {
				continue;
			}

			XenoRegionCompiler.MeshSlice slice = compiler.resolveSlice(compiled, layer);
			boolean customIndicesReady = !draw.hasCustomIndexBuffer()
					|| (slice != null && slice.indices() != null && compiled.isIndexBufferUploaded(layer));
			if (!customIndicesReady || slice == null) {
				continue;
			}

			if (uboIndex == -1) {
				uboIndex = sectionInfos.size();
				sectionInfos.add(
						new DynamicUniforms.ChunkSectionInfo(modelViewMatrix, originX, originY, originZ, visibility, atlasWidth, atlasHeight)
				);
			}

			VertexFormat vertexFormat = layer.pipeline().getVertexFormatBinding(0);
			GpuBuffer vertexBuffer = slice.vertices();
			int combinedHash = 173;
			if (layer != ChunkSectionLayer.TRANSLUCENT) {
				combinedHash = 31 * combinedHash + vertexBuffer.hashCode();
			}

			int firstIndex = 0;
			GpuBuffer indexBuffer = null;
			IndexType indexType = null;
			if (draw.hasCustomIndexBuffer()) {
				indexBuffer = slice.indices();
				indexType = draw.indexType();
				if (layer != ChunkSectionLayer.TRANSLUCENT) {
					combinedHash = 31 * combinedHash + indexBuffer.hashCode();
					combinedHash = 31 * combinedHash + indexType.hashCode();
				}

				firstIndex = (int)(slice.indexOffset() / indexType.bytes);
			} else if (draw.indexCount() > largestIndexCount[0]) {
				largestIndexCount[0] = draw.indexCount();
			}

			int finalUboIndex = uboIndex;
			int baseVertex = (int)(slice.vertexOffset() / Objects.requireNonNull(vertexFormat).getVertexSize());
			List<RenderPass.Draw<GpuBufferSlice[]>> draws = drawGroups.get(layer)
					.computeIfAbsent(combinedHash, key -> new ArrayList<>());
			draws.add(
					new RenderPass.Draw<>(
							0, vertexBuffer, indexBuffer, indexType, firstIndex, draw.indexCount(), baseVertex,
							(sectionUbos, uploader) -> uploader.upload("ChunkSection", sectionUbos[finalUboIndex])
					)
			);
		}
	}
}
