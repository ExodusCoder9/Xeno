package com.xeno.client.render.buffer;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.xeno.client.render.XenoWorldRenderer;
import com.xeno.client.render.chunk.compile.ChunkCompileResult;
import com.xeno.client.render.chunk.storage.XenoSection;
import com.xeno.client.render.chunk.terrain.RenderRegion;
import com.xeno.client.render.chunk.terrain.RenderRegionManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import java.nio.ByteBuffer;

public class UploadManager {
	private final StagingBuffer staging;
	private final BufferAllocator allocator = new BufferAllocator();
	private final ObjectArrayList<PendingUpload> pendingUploads = new ObjectArrayList<>();

	public UploadManager(StagingBuffer staging) {
		this.staging = staging;
	}

	public void uploadMesh(XenoSection section, Object mesh) {
		if (mesh instanceof ChunkCompileResult result) {
			if (!result.success()) return;

			int rx = section.getX() >> 3;
			int ry = section.getY() >> 3;
			int rz = section.getZ() >> 3;
			
			RenderRegionManager regionManager = XenoWorldRenderer.getInstance().getRegionManager();
			RenderRegion region = regionManager.getOrCreateRegion(rx, ry, rz);

			for (ChunkCompileResult.LayerResult lr : result.layers()) {
				if (lr.vertexCount() == 0) continue;

				ChunkSectionLayer layer = lr.layer();
				
				TlsfAllocator.Allocation oldV = section.getVertexAllocation(layer);
				if (oldV != null) {
					allocator.freeVertex(region.getRegionKey(), oldV);
				}
				TlsfAllocator.Allocation oldI = section.getIndexAllocation(layer);
				if (oldI != null) {
					allocator.freeIndex(region.getRegionKey(), oldI);
				}

				int vSize = lr.vertexData().remaining();
				int iSize = lr.indexData() != null ? lr.indexData().remaining() : 0;

				TlsfAllocator.Allocation vAlloc = allocator.allocateVertex(region.getRegionKey(), vSize);
				TlsfAllocator.Allocation iAlloc = iSize > 0 ? allocator.allocateIndex(region.getRegionKey(), iSize) : null;

				if (vAlloc == null) {
					continue;
				}

				section.setVertexAllocation(layer, vAlloc);
				section.setIndexAllocation(layer, iAlloc);
				section.setVertexCount(layer, lr.vertexCount());
				section.setIndexCount(layer, lr.indexCount());

				long vSrcOffset = staging.reserve(region.getRegionKey(), vSize);
				long iSrcOffset = iSize > 0 ? staging.reserve(region.getRegionKey(), iSize) : 0;

				ByteBuffer vMap = staging.map(vSrcOffset, vSize);
				vMap.put(lr.vertexData().duplicate());
				staging.unmap();

				if (iSize > 0 && iAlloc != null) {
					ByteBuffer iMap = staging.map(iSrcOffset, iSize);
					iMap.put(lr.indexData().duplicate());
					staging.unmap();
				}

				pendingUploads.add(new PendingUpload(
					region,
					layer,
					vAlloc.getOffsetFromHeap(),
					iAlloc != null ? iAlloc.getOffsetFromHeap() : 0,
					vSrcOffset,
					iSrcOffset,
					vSize,
					iSize
				));
			}
		}
	}

	public void flush(GpuDevice device) {
		if (pendingUploads.isEmpty()) return;

		CommandEncoder encoder = device.createCommandEncoder();
		for (PendingUpload upload : pendingUploads) {
			RenderRegion region = upload.region();
			region.initBuffers(device);

			staging.submitUpload(
				encoder,
				region.getVertexBuffer(),
				upload.srcVertexOffset(),
				upload.destVertexOffset(),
				upload.vertexSize()
			);

			if (upload.indexSize() > 0) {
				staging.submitUpload(
					encoder,
					region.getIndexBuffer(),
					upload.srcIndexOffset(),
					upload.destIndexOffset(),
					upload.indexSize()
				);
			}
		}
		encoder.submit();
		pendingUploads.clear();
	}

	public void processStagedAllocations(GpuDevice device) {
	}

	public void releaseSection(XenoSection section) {
		int rx = section.getX() >> 3;
		int ry = section.getY() >> 3;
		int rz = section.getZ() >> 3;
		long key = RenderRegionManager.regionKey(rx, ry, rz);
		for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
			TlsfAllocator.Allocation v = section.getVertexAllocation(layer);
			if (v != null) allocator.freeVertex(key, v);
			TlsfAllocator.Allocation i = section.getIndexAllocation(layer);
			if (i != null) allocator.freeIndex(key, i);
			section.setVertexAllocation(layer, null);
			section.setIndexAllocation(layer, null);
			section.setVertexCount(layer, 0);
			section.setIndexCount(layer, 0);
		}
	}

	public long freeStagingSpace() { return 16 * 1024 * 1024; }

	private record PendingUpload(
		RenderRegion region,
		ChunkSectionLayer layer,
		long destVertexOffset,
		long destIndexOffset,
		long srcVertexOffset,
		long srcIndexOffset,
		int vertexSize,
		int indexSize
	) {}
}
