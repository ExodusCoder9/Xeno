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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
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
	private static final int STAGED = 0;
	private static final int RETRY = 1;
	private static final int ABORT = 2;
	private final ReentrantLock copyLock = new ReentrantLock();
	private final EnumMap<ChunkSectionLayer, LayerBuffers> layers = new EnumMap<>(ChunkSectionLayer.class);
	private final AtomicBoolean initialized = new AtomicBoolean(false);
	private @Nullable StagingBuffer stagingBuffer;
	private @Nullable SectionBufferBuilderPool bufferPool;
	private volatile boolean disposed;
	private volatile @Nullable SectionCompiler sectionCompiler;
	private long submittedTasks;
	private long completedTasks;
	private long retriedTasks;
	private long droppedTasks;
	private XenoRegionCompiler() {
	}

	/**
	 * Must run on the render thread with an alive GpuDevice.
	 */
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

			int packs = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 6);
			this.bufferPool = SectionBufferBuilderPool.allocate(packs);
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

	public void lock() {
		this.copyLock.lock();
	}

	public void unlock() {
		this.copyLock.unlock();
	}

	public void submit(XenoRenderRegion region, int localIndex, RenderSectionRegion snapshot, VertexSorting sorting, Vec3 cameraPos) {
		if (this.disposed || !region.alive.get()) {
			this.droppedTasks++;
			return;
		}

		this.ensureInitialized();
		this.submittedTasks++;
		XenoChunkExecutorService.INSTANCE.execute(() -> this.compileTask(region, localIndex, snapshot, sorting, cameraPos));
	}

	private void compileTask(XenoRenderRegion region, int localIndex, RenderSectionRegion snapshot, VertexSorting sorting, Vec3 cameraPos) {
		if (this.disposed || !region.alive.get()) {
			this.droppedTasks++;
			return;
		}

		int sectionsXZ = XenoWorldRenderManager.REGION_SECTIONS_XZ;
		int localX = localIndex % sectionsXZ;
		int localZ = localIndex / sectionsXZ % sectionsXZ;
		int localY = localIndex / (sectionsXZ * sectionsXZ);
		SectionPos sectionPos = SectionPos.of(
			region.minSectionX() + localX, region.minSectionY() + localY, region.minSectionZ() + localZ
		);

		SectionBufferBuilderPack pack = Objects.requireNonNull(this.bufferPool).acquire();
		if (pack == null) {
			if (region.alive.get()) {
				region.markSectionDirty(localIndex, false);
				this.retriedTasks++;
			}

			return;
		}

		try {
			SectionCompiler compiler = this.sectionCompiler;
			if (compiler == null || !region.alive.get()) {
				if (region.alive.get() && compiler == null) {
					region.markSectionDirty(localIndex, false);
				}

				this.droppedTasks++;
				return;
			}

			SectionCompiler.Results results = compiler.compile(sectionPos, snapshot, sorting, pack);
			CompiledSectionMesh mesh = new CompiledSectionMesh(
				TranslucencyPointOfView.of(cameraPos, sectionPos.asLong()), results
			);

			if (results.renderedLayers.isEmpty()) {
				this.publish(region, localIndex, mesh);
				this.completedTasks++;
				return;
			}

			for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
				MeshData meshData = entry.getValue();
				while (true) {
					int outcome = this.stageLayerBuffers(entry.getKey(), mesh, meshData, region);
					if (outcome == STAGED) {
						break;
					}

					if (outcome == ABORT || this.disposed || !region.alive.get()) {
						results.release();
						this.releaseMeshAllocations(mesh);
						this.droppedTasks++;
						return;
					}

					Thread.onSpinWait();
				}

				meshData.close();
			}

			this.publish(region, localIndex, mesh);
			this.completedTasks++;
		} catch (Throwable t) {
			LOGGER.error("Section compile failed at {}", sectionPos, t);
			if (region.alive.get()) {
				region.markSectionDirty(localIndex, false);
			}

			this.droppedTasks++;
		} finally {
			pack.clearAll();
			this.bufferPool.release(pack);
		}
	}

	private int stageLayerBuffers(ChunkSectionLayer layer, CompiledSectionMesh mesh, MeshData meshData, XenoRenderRegion region) {
		LayerBuffers buffers = this.layers.get(layer);
		this.copyLock.lock();

		try {
			if (this.disposed || !region.alive.get()) {
				return ABORT;
			}

			boolean success = true;
			ByteBuffer vertices = meshData.vertexBuffer();
			if (vertices != null) {
				success &= buffers.vertices.addAllocation(mesh, m -> ((CompiledSectionMesh)m).setVertexBufferUploaded(layer), vertices);
			}

			ByteBuffer indices = meshData.indexBuffer();
			if (indices != null) {
				success &= buffers.indices.addAllocation(mesh, m -> ((CompiledSectionMesh)m).setIndexBufferUploaded(layer), indices);
			} else {
				mesh.setIndexBufferUploaded(layer);
			}

			return success ? STAGED : RETRY;
		} finally {
			this.copyLock.unlock();
		}
	}

	private void publish(XenoRenderRegion region, int localIndex, SectionMesh mesh) {
		this.copyLock.lock();

		try {
			SectionMesh old = region.meshSlot(localIndex).getAndSet(mesh);
			this.releaseMeshAllocations(old);
			region.noteMeshUploaded(localIndex, Util.getMillis());
		} finally {
			this.copyLock.unlock();
		}
	}

	public void releaseRegion(XenoRenderRegion region) {
		region.alive.set(false);
		this.copyLock.lock();

		try {
			for (int i = 0; i < XenoRenderRegion.SECTION_COUNT; i++) {
				SectionMesh old = region.meshSlot(i).getAndSet(CompiledSectionMesh.UNCOMPILED);
				this.releaseMeshAllocations(old);
			}
		} finally {
			this.copyLock.unlock();
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
		this.copyLock.lock();

		try (StagingBuffer.Uploader uploader = staging.startUploading(device.createCommandEncoder())) {
			for (LayerBuffers buffers : this.layers.values()) {
				boolean resizedHeap = buffers.vertices.uploadStagedAllocations(device, uploader);
				buffers.indices.uploadStagedAllocations(device, uploader);
				if (resizedHeap) {
					break;
				}
			}
		} finally {
			this.copyLock.unlock();
		}
	}

	public String getStats() {
		return String.format("submitted: %d, done: %d, retry: %d, drop: %d", this.submittedTasks, this.completedTasks, this.retriedTasks, this.droppedTasks);
	}

	private record LayerBuffers(UberGpuBuffer<SectionMesh> vertices, UberGpuBuffer<SectionMesh> indices) {
	}
}
