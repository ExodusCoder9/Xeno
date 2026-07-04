package com.xeno.client.render.buffer;

import com.mojang.blaze3d.systems.GpuDevice;
import com.xeno.client.render.chunk.storage.XenoSection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class UploadManager {
	private final StagingBuffer staging;
	private final ObjectArrayList<PendingUpload> pendingUploads = new ObjectArrayList<>();

	public UploadManager(StagingBuffer staging) {
		this.staging = staging;
	}

	public void uploadMesh(XenoSection section, Object mesh) {
	}

	public void flush(GpuDevice device) {
	}

	public void processStagedAllocations(GpuDevice device) {
	}

	public void releaseSection(XenoSection section) {
	}

	public long freeStagingSpace() { return 0; }

	private record PendingUpload(
		long regionKey, int sectionIndex,
		java.nio.ByteBuffer vertexData, java.nio.ByteBuffer indexData,
		int vertexSize, int indexSize
	) {}
}
