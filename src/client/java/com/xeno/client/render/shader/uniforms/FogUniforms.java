package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.fog.FogData;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public class FogUniforms {
    private static final int SIZE = 48;
    private final UniformBuffer buffer;

    public FogUniforms(GpuDevice device) {
        this.buffer = new UniformBuffer(device, SIZE, 1);
    }

    public void update(FogData fog, Vector4f color) {
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, SIZE)
                .putVec4(color.x, color.y, color.z, color.w)
                .putFloat(fog == null ? 0.0F : fog.environmentalStart)
                .putFloat(fog == null ? 0.0F : fog.renderDistanceStart)
                .putFloat(fog == null ? 1.0F : fog.environmentalEnd)
                .putFloat(fog == null ? 1.0F : fog.renderDistanceEnd)
                .get();
            buffer.update(0, data);
        }
    }

    public GpuBufferSlice getSlice() { return buffer.getSlice(); }

    public void bind(RenderPass pass) {
        pass.setUniform("FogParams", buffer.getSlice());
    }

    public void close() { buffer.close(); }
}
