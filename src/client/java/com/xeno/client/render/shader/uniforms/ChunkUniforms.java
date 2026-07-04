package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import org.lwjgl.system.MemoryStack;

public class ChunkUniforms {
    private static final int VEC4_SIZE = 16;
    private static final int MAX_REGIONS = 256;
    private final UniformBuffer buffer;

    public ChunkUniforms(GpuDevice device) {
        this.buffer = new UniformBuffer(device, VEC4_SIZE, MAX_REGIONS);
    }

    public void setRegionOffset(int regionIndex, float x, float y, float z) {
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, VEC4_SIZE)
                .putVec4(x, y, z, 0.0F)
                .get();
            buffer.update(regionIndex, data);
        }
    }

    public GpuBufferSlice getSlice(int index) { return buffer.getSlice(index); }

    public void bind(RenderPass pass) {
        pass.setUniform("ChunkOffsets", buffer.getSlice());
    }

    public void close() { buffer.close(); }
}
