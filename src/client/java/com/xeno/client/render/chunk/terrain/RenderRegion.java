package com.xeno.client.render.chunk.terrain;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;

public class RenderRegion {
	public static final int SIZE_SECTIONS = 8;

	private final int rx, ry, rz;
	private final long regionKey;
	private GpuBuffer vertexBuffer;
	private GpuBuffer indexBuffer;

	public RenderRegion(int rx, int ry, int rz, long regionKey) {
		this.rx = rx;
		this.ry = ry;
		this.rz = rz;
		this.regionKey = regionKey;
	}

	public long getRegionKey() { return regionKey; }
	public int getRx() { return rx; }
	public int getRy() { return ry; }
	public int getRz() { return rz; }

	public GpuBuffer getVertexBuffer() { return vertexBuffer; }
	public GpuBuffer getIndexBuffer() { return indexBuffer; }

	public void initBuffers(GpuDevice device) {
		if (vertexBuffer == null) {
			vertexBuffer = device.createBuffer(
				() -> "XenoRegion-Vertex-" + regionKey,
				GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
				4 * 1024 * 1024
			);
		}
		if (indexBuffer == null) {
			indexBuffer = device.createBuffer(
				() -> "XenoRegion-Index-" + regionKey,
				GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
				1 * 1024 * 1024
			);
		}
	}

	public void close() {
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
		if (indexBuffer != null) {
			indexBuffer.close();
			indexBuffer = null;
		}
	}
}
