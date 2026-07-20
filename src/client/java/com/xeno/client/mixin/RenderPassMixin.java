package com.xeno.client.mixin;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.xeno.client.renderer.pass.XenoBatchRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(RenderPass.class)
public abstract class RenderPassMixin {
    @Shadow public abstract void setVertexBuffer(int slot, GpuBufferSlice vertexBuffer);
    @Shadow public abstract void setIndexBuffer(GpuBuffer indexBuffer, IndexType indexType);
    @Shadow public abstract void setUniform(String name, GpuBufferSlice value);
    @Shadow public abstract void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance);

    @Inject(method = "drawMultipleIndexed", at = @At("HEAD"), cancellable = true)
    private <T> void xenoDrawMultipleIndexed(
            Collection<RenderPass.Draw<T>> draws,
            GpuBuffer defaultIndexBuffer,
            IndexType defaultIndexType,
            Collection<String> dynamicUniforms,
            T uniformArgument,
            CallbackInfo ci
    ) {
        if (XenoBatchRenderer.drawMultipleIndexed(
                draws, defaultIndexBuffer, defaultIndexType, uniformArgument,
                this::setVertexBuffer, this::setIndexBuffer, this::setUniform, this::drawIndexed
        )) {
            ci.cancel();
        }
    }
}
