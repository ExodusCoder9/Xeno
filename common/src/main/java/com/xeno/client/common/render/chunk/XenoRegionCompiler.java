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

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.StagingBuffer;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class XenoRegionCompiler {
	public static final Logger LOGGER = LoggerFactory.getLogger("Xeno/XenoRegionCompiler");
	public static final XenoRegionCompiler INSTANCE = new XenoRegionCompiler();
	private static final int VERTEX_HEAP_BYTES = 128 * 1024 * 1024;
	private static final int INDEX_HEAP_BYTES = 32 * 1024 * 1024;
	private static final int STAGING_BUFFER_BYTES = 96 * 1024 * 1024;

	private final EnumMap<ChunkSectionLayer, LayerBuffers> layers = new EnumMap<>(ChunkSectionLayer.class);
	private final AtomicBoolean initialized = new AtomicBoolean(false);
	private final ConcurrentLinkedQueue<CompileResult> uploadQueue = new ConcurrentLinkedQueue<>();
	private @Nullable StagingBuffer stagingBuffer;
	private @Nullable SectionBufferBuilderPool bufferPool;
	private @Nullable Semaphore packPermits;
	private volatile @Nullable SectionCompiler sectionCompiler;

	public record CompileResult(
			XenoRenderRegion region,
			int localIndex,
			SectionCompiler.Results results,
			CompiledSectionMesh mesh,
			SectionBufferBuilderPack pack
	) {}

	private XenoRegionCompiler() {
	}

	private void ensureInitialized() {
		if (this.initialized.compareAndSet(false, true)) {
			GpuDevice device = RenderSystem.getDevice();
			this.stagingBuffer = StagingBuffer.create("XenoChunk", device, STAGING_BUFFER_BYTES);
			for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
				String label = "xeno_" + layer.label();
				UberGpuBuffer<SectionMesh> vertices = new UberGpuBuffer<>(
						label, 32, VERTEX_HEAP_BYTES, layer.vertexFormat().getVertexSize(), this.stagingBuffer
				);
				UberGpuBuffer<SectionMesh> indices = new UberGpuBuffer<>(label, 64, INDEX_HEAP_BYTES, 8, this.stagingBuffer);
				this.layers.put(layer, new LayerBuffers(vertices, indices));
			}
			int packs = XenoChunkExecutorService.optimalWorkerCount();
			this.bufferPool = SectionBufferBuilderPool.allocate(packs);
			this.packPermits = new Semaphore(packs);
			LOGGER.info("Initialized region compiler: {} layer pairs, {} builder packs", this.layers.size(), packs);
		}
	}

	public void setCompiler(@Nullable SectionCompiler compiler) {
		this.sectionCompiler = compiler;
	}

	public record MeshSlice(GpuBuffer vertices, long vertexOffset, @Nullable GpuBuffer indices, long indexOffset) {
	}

	public @Nullable MeshSlice resolveSlice(SectionMesh mesh, ChunkSectionLayer layer) {
		LayerBuffers buffers = this.layers.get(layer);
		if (buffers == null) {
			return null;
		}
		TlsfAllocator.Allocation vertexAlloc = buffers.vertices.getAllocation(mesh);
		if (vertexAlloc == null) {
			return null;
		}

		TlsfAllocator.Allocation indexAlloc = buffers.indices.getAllocation(mesh);
		return new MeshSlice(
				buffers.vertices.getGpuBuffer(vertexAlloc),
				vertexAlloc.getOffsetFromHeap(),
				indexAlloc != null ? buffers.indices.getGpuBuffer(indexAlloc) : null,
				indexAlloc != null ? indexAlloc.getOffsetFromHeap() : 0L
		);
	}

	public void submit(XenoRenderRegion region, int localIndex, RenderSectionRegion snapshot, VertexSorting sorting, Vec3 cameraPos) {
		if (!region.alive.get()) {
			return;
		}

		this.ensureInitialized();
		XenoChunkExecutorService.INSTANCE.execute(() -> this.compileTask(region, localIndex, snapshot, sorting, cameraPos));
	}

	private void compileTask(XenoRenderRegion region, int localIndex, RenderSectionRegion snapshot, VertexSorting sorting, Vec3 cameraPos) {
		if (!region.alive.get()) {
			return;
		}

		int sectionsXZ = XenoWorldRenderManager.REGION_SECTIONS_XZ;
		int localX = localIndex % sectionsXZ;
		int localZ = localIndex / sectionsXZ % sectionsXZ;
		int localY = localIndex / (sectionsXZ * sectionsXZ);
		SectionPos sectionPos = SectionPos.of(
				region.minSectionX() + localX, region.minSectionY() + localY, region.minSectionZ() + localZ
		);

		SectionBufferBuilderPool pool = Objects.requireNonNull(this.bufferPool);
		Semaphore permits = Objects.requireNonNull(this.packPermits);
		try {
			permits.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (region.alive.get()) {
				region.clearPending(localIndex);
				region.markSectionDirty(localIndex, false);
			}
			return;
		}

		SectionBufferBuilderPack pack = pool.acquire();
		boolean handedOff = false;
		try {
			SectionCompiler compiler = this.sectionCompiler;
			if (compiler == null || !region.alive.get()) {
				if (region.alive.get()) {
					region.clearPending(localIndex);
					if (compiler == null) {
						region.markSectionDirty(localIndex, false);
					}
				}
				return;
			}

			SectionCompiler.Results results = compiler.compile(sectionPos, snapshot, sorting, Objects.requireNonNull(pack));
			CompiledSectionMesh mesh = new CompiledSectionMesh(
					TranslucencyPointOfView.of(cameraPos, sectionPos.asLong()), results
			);

			if (!region.alive.get()) {
				results.release();
				region.clearPending(localIndex);
				return;
			}

			// Pass pack and results to Render Thread for staging & uploading
			this.uploadQueue.add(new CompileResult(region, localIndex, results, mesh, pack));
			handedOff = true;
		} catch (Throwable t) {
			LOGGER.error("Section compile failed at {}", sectionPos, t);
			if (region.alive.get()) {
				region.clearPending(localIndex);
				region.markSectionDirty(localIndex, false);
			}
		} finally {
			if (!handedOff) {
				pack.clearAll();
				pool.release(pack);
				permits.release();
			}
		}
	}

	/**
	 * Must run on the Render Thread.
	 */
	public void processPendingUploadsAndFlush() {
		if (!this.initialized.get()) {
			return;
		}

		SectionBufferBuilderPool pool = Objects.requireNonNull(this.bufferPool);
		Semaphore permits = Objects.requireNonNull(this.packPermits);

		CompileResult result;
		while ((result = this.uploadQueue.poll()) != null) {
			XenoRenderRegion region = result.region();
			int localIndex = result.localIndex();
			SectionCompiler.Results results = result.results();
			CompiledSectionMesh mesh = result.mesh();
			SectionBufferBuilderPack pack = result.pack();

			try {
				if (!region.alive.get()) {
					results.release();
					region.clearPending(localIndex);
					continue;
				}

				if (!results.renderedLayers.isEmpty()) {
					for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
						ChunkSectionLayer layer = entry.getKey();
						MeshData meshData = entry.getValue();
						this.stageLayerBuffersOnRenderThread(layer, mesh, meshData);
						meshData.close();
					}
				}

				this.publishOnRenderThread(region, localIndex, mesh);
			} finally {
				pack.clearAll();
				pool.release(pack);
				permits.release();
			}
		}

		this.flushUploads();
	}

	private void stageLayerBuffersOnRenderThread(ChunkSectionLayer layer, CompiledSectionMesh mesh, MeshData meshData) {
		LayerBuffers buffers = this.layers.get(layer);
		if (buffers == null) {
			return;
		}

		ByteBuffer vertices = meshData.vertexBuffer();
		if (vertices != null) {
			buffers.vertices.addAllocation(mesh, m -> {
				if (m instanceof CompiledSectionMesh compiled) {
					try {
						compiled.setVertexBufferUploaded(layer);
					} catch (Throwable ignored) {
					}
				}
			}, vertices);
		}

		ByteBuffer indices = meshData.indexBuffer();
		if (indices != null) {
			buffers.indices.addAllocation(mesh, m -> {
				if (m instanceof CompiledSectionMesh compiled) {
					try {
						compiled.setIndexBufferUploaded(layer);
					} catch (Throwable ignored) {
					}
				}
			}, indices);
		} else {
			try {
				mesh.setIndexBufferUploaded(layer);
			} catch (Throwable ignored) {
			}
		}
	}

	private void publishOnRenderThread(XenoRenderRegion region, int localIndex, SectionMesh mesh) {
		SectionMesh old = region.meshSlot(localIndex).getAndSet(mesh);
		this.releaseMeshAllocations(old);
		region.clearPending(localIndex);
		region.noteMeshUploaded(localIndex, Util.getMillis());

		int sectionsXZ = XenoWorldRenderManager.REGION_SECTIONS_XZ;
		int localX = localIndex % sectionsXZ;
		int localZ = localIndex / sectionsXZ % sectionsXZ;
		int localY = localIndex / (sectionsXZ * sectionsXZ);
		long sectionNode = SectionPos.asLong(region.minSectionX() + localX, region.minSectionY() + localY, region.minSectionZ() + localZ);

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer != null && minecraft.levelRenderer.viewArea() != null) {
			SectionRenderDispatcher.RenderSection vanillaSection = ((com.xeno.client.mixin.ViewAreaAccessor) Objects.requireNonNull(minecraft.levelRenderer.viewArea())).xeno$getRenderSection(sectionNode);
			if (vanillaSection != null) {
				vanillaSection.sectionMesh.set(mesh);
				minecraft.levelRenderer.sectionOcclusionGraph().schedulePropagationFrom(vanillaSection);
			}
		}
	}

	public void releaseRegion(XenoRenderRegion region) {
		region.alive.set(false);

		Minecraft minecraft = Minecraft.getInstance();
		for (int i = 0; i < XenoRenderRegion.SECTION_COUNT; i++) {
			SectionMesh old = region.meshSlot(i).getAndSet(CompiledSectionMesh.UNCOMPILED);
			this.releaseMeshAllocations(old);
			region.clearPending(i);

			if (minecraft.levelRenderer != null && minecraft.levelRenderer.viewArea() != null) {
				int sectionsXZ = XenoWorldRenderManager.REGION_SECTIONS_XZ;
				int localX = i % sectionsXZ;
				int localZ = i / sectionsXZ % sectionsXZ;
				int localY = i / (sectionsXZ * sectionsXZ);
				long sectionNode = SectionPos.asLong(region.minSectionX() + localX, region.minSectionY() + localY, region.minSectionZ() + localZ);
				SectionRenderDispatcher.RenderSection vanillaSection = ((com.xeno.client.mixin.ViewAreaAccessor) Objects.requireNonNull(minecraft.levelRenderer.viewArea())).xeno$getRenderSection(sectionNode);
				if (vanillaSection != null) {
					vanillaSection.sectionMesh.set(CompiledSectionMesh.UNCOMPILED);
				}
			}
		}
	}

	private void releaseMeshAllocations(@Nullable SectionMesh mesh) {
		if (!(mesh instanceof CompiledSectionMesh compiled)) {
			return;
		}

		for (LayerBuffers buffers : this.layers.values()) {
			buffers.vertices.removeAllocation(compiled);
			buffers.indices.removeAllocation(compiled);
		}
	}

	public void flushUploads() {
		StagingBuffer staging = this.stagingBuffer;
		if (!this.initialized.get() || staging == null) {
			return;
		}

		GpuDevice device = RenderSystem.getDevice();
		try (StagingBuffer.Uploader uploader = staging.startUploading(device.createCommandEncoder())) {
			boolean restart;
			do {
				restart = false;
				for (LayerBuffers buffers : this.layers.values()) {
					boolean resizedHeap = buffers.vertices.uploadStagedAllocations(device, uploader);
					resizedHeap |= buffers.indices.uploadStagedAllocations(device, uploader);
					if (resizedHeap) {
						restart = true;
						break;
					}
				}
			} while (restart);
		}
	}

	private record LayerBuffers(UberGpuBuffer<SectionMesh> vertices, UberGpuBuffer<SectionMesh> indices) {
	}
}