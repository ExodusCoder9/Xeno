package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public class EntityUniforms {
    private static final int SIZE = 80;
    private final UniformBuffer buffer;

    public EntityUniforms(GpuDevice device) {
        this.buffer = new UniformBuffer(device, SIZE, 1);
    }

    public void update(Matrix4f modelMatrix, Vector4f color) {
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, SIZE)
                .putMat4f(modelMatrix)
                .putVec4(color.x, color.y, color.z, color.w)
                .get();
            buffer.update(0, data);
        }
    }

    public GpuBufferSlice getSlice() { return buffer.getSlice(); }

    public void bind(RenderPass pass) {
        pass.setUniform("EntityMat", buffer.getSlice());
    }

    public void close() { buffer.close(); }
}
