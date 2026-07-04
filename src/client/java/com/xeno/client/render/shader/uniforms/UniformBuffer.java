package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class UniformBuffer {
    private final GpuBuffer buffer;
    private final int size;
    private final GpuBufferSlice slice;

    public UniformBuffer(GpuDevice device, int size, int capacity) {
        this.size = size;
        this.buffer = device.createBuffer(
            (Supplier<String>) () -> "XenoUBO",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
            (long) size * capacity
        );
        this.slice = buffer.slice();
    }

    public int getSize() { return size; }

    public GpuBufferSlice getSlice() { return slice; }

    public GpuBufferSlice getSlice(int index) {
        return buffer.slice((long) index * size, size);
    }

    public void update(int index, ByteBuffer data) {
        long offset = (long) index * size;
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
            buffer.slice(offset, data.remaining()), data
        );
    }

    public void close() { buffer.close(); }
}
