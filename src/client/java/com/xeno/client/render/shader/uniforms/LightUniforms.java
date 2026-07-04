package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public class LightUniforms {
    private static final int SIZE = 32;
    private final UniformBuffer buffer;

    public LightUniforms(GpuDevice device) {
        this.buffer = new UniformBuffer(device, SIZE, 1);
    }

    public void update(Vector4f skyColor, Vector4f blockLightColor) {
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, SIZE)
                .putVec4(skyColor.x, skyColor.y, skyColor.z, skyColor.w)
                .putVec4(blockLightColor.x, blockLightColor.y, blockLightColor.z, blockLightColor.w)
                .get();
            buffer.update(0, data);
        }
    }

    public GpuBufferSlice getSlice() { return buffer.getSlice(); }

    public void bind(RenderPass pass) {
        pass.setUniform("LightParams", buffer.getSlice());
    }

    public void close() { buffer.close(); }
}
