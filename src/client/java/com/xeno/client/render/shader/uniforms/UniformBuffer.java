package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import java.nio.ByteBuffer;

public class UniformBuffer {
	private final GpuBuffer buffer;
	private final int stride;
	private final int capacity;
	private int writeOffset;

	public UniformBuffer(GpuDevice device, int stride, int capacity) {
		this.stride = stride;
		this.capacity = capacity;
		this.buffer = device.createBuffer(
			() -> "Xeno-Uniform",
			GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_HINT_CLIENT_STORAGE,
			(long) stride * capacity
		);
	}

	public void write(int index, ByteBuffer data) {}
	public void writeAll(ByteBuffer data) {}
	public GpuBufferSlice getSlice() { return buffer.slice(0, stride); }
	public GpuBufferSlice getSlice(int index) { return buffer.slice((long) index * stride, stride); }
	public void reset() { writeOffset = 0; }
	public void close() { buffer.close(); }
}
