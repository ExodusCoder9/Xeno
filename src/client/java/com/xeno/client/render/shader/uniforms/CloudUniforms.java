package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public class CloudUniforms {
    private static final int SIZE = 32;
    private final UniformBuffer buffer;

    public CloudUniforms(GpuDevice device) {
        this.buffer = new UniformBuffer(device, SIZE, 1);
    }

    public void update(Vector4f color, float cloudHeight, float density, float coverage) {
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, SIZE)
                .putVec4(color.x, color.y, color.z, color.w)
                .putVec4(cloudHeight, density, coverage, 0.0F)
                .get();
            buffer.update(0, data);
        }
    }

    public GpuBufferSlice getSlice() { return buffer.getSlice(); }

    public void bind(RenderPass pass) {
        pass.setUniform("CloudParams", buffer.getSlice());
    }

    public void close() { buffer.close(); }
}
