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
	private final it.unimi.dsi.fastutil.longs.LongArrayList nodeScratch = new it.unimi.dsi.fastutil.longs.LongArrayList();
	private XenoRenderRegion[] resolvedRegions = new XenoRenderRegion[0];
	private int[] resolvedIndices = new int[0];
	private final EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups =
			new EnumMap<>(ChunkSectionLayer.class);
	private final List<DynamicUniforms.ChunkSectionInfo> sectionInfos = new ArrayList<>();
	private final int[] largestIndexCount = new int[1];

	private XenoTerrainDrawer() {
		for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
			this.drawGroups.put(layer, new Int2ObjectOpenHashMap<>());
		}
	}

	public ChunkSectionsToRender prepareChunkRenders(LevelRenderer renderer, Matrix4fc modelViewMatrix) {
		Minecraft minecraft = Minecraft.getInstance();

		for (Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> group : this.drawGroups.values()) {
			group.values().forEach(List::clear);
		}

		this.sectionInfos.clear();
		this.largestIndexCount[0] = 0;
		GpuTextureView blockAtlasView = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		int atlasWidth = blockAtlasView.getWidth(0);
		int atlasHeight = blockAtlasView.getHeight(0);
		long now = Util.getMillis();
		XenoRegionCompiler compiler = XenoRegionCompiler.INSTANCE;
		compiler.processPendingUploadsAndFlush();

		it.unimi.dsi.fastutil.longs.LongArrayList nodes = this.nodeScratch;
		nodes.clear();
		int visibleCount = 0;
		for (SectionRenderDispatcher.RenderSection visible : renderer.visibleSections) {
			nodes.add(visible.getSectionNode());
			visibleCount++;
		}

		if (visibleCount > this.resolvedRegions.length) {
			int capacity = Math.max(visibleCount, this.resolvedRegions.length * 2);
			this.resolvedRegions = new XenoRenderRegion[capacity];
			this.resolvedIndices = new int[capacity];
		}

		XenoWorldRenderManager.INSTANCE.resolveSections(nodes, this.resolvedRegions, this.resolvedIndices, visibleCount);

		try {
			for (int i = 0; i < visibleCount; i++) {
				long node = nodes.getLong(i);
				XenoRenderRegion region = this.resolvedRegions[i];
				if (region == null) {
					this.missingScratch.add(node);
					continue;
				}

				int localIndex = this.resolvedIndices[i];
				SectionMesh mesh = region.meshSlot(localIndex).get();
				if (mesh == null || mesh == CompiledSectionMesh.UNCOMPILED) {
					// Only nudge sections that have neither a mesh nor a queued compile;
					// re-marking pending ones every frame flooded the queue with duplicates.
					if (!region.isPending(localIndex)) {
						this.missingScratch.add(node);
					}
					continue;
				}

				if (!(mesh instanceof CompiledSectionMesh compiled) || !compiled.hasRenderableLayers()) {
					continue;
				}

				float visibility = region.visibilityAt(localIndex, now);
				appendSectionDraws(
						this.drawGroups, this.sectionInfos, this.largestIndexCount, compiler, compiled, visibility,
						SectionPos.sectionToBlockCoord(SectionPos.x(node)),
						SectionPos.sectionToBlockCoord(SectionPos.y(node)),
						SectionPos.sectionToBlockCoord(SectionPos.z(node)),
						atlasWidth, atlasHeight, modelViewMatrix
				);
			}
		} finally {
			java.util.Arrays.fill(this.resolvedRegions, 0, visibleCount, null);
		}

		XenoWorldRenderManager.INSTANCE.markSectionsMissing(this.missingScratch, SELF_HEAL_MARKS_PER_FRAME);
		this.missingScratch.clear();

		for (Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> group : this.drawGroups.values()) {
			group.values().removeIf(List::isEmpty);
		}

		SHARED_INDEX_BUFFER.ensureCapacity(Math.max(this.largestIndexCount[0], 1));
		var uniformSlices = RenderSystem.getDynamicUniforms()
				.writeChunkSections(this.sectionInfos.toArray(new DynamicUniforms.ChunkSectionInfo[0]));
		return new ChunkSectionsToRender(blockAtlasView, this.drawGroups, this.largestIndexCount[0], uniformSlices);
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
