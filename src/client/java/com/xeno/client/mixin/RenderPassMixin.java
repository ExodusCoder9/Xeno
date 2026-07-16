package com.xeno.client.mixin;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.xeno.client.renderer.XenoWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(RenderPass.class)
public abstract class RenderPassMixin {
    @Shadow public abstract void setVertexBuffer(int slot, GpuBufferSlice buffer);
    @Shadow public abstract void setIndexBuffer(GpuBuffer buffer, IndexType type);
    @Shadow public abstract void setUniform(String name, GpuBufferSlice slice);
    @Shadow public abstract void drawIndexed(int count, int instanceCount, int firstIndex, int baseVertex, int baseInstance);

    @Inject(method = "drawMultipleIndexed", at = @At("HEAD"), cancellable = true)
    private <T> void xenoDrawMultipleIndexed(
            Collection<RenderPass.Draw<T>> draws,
            GpuBuffer indexBuffer,
            IndexType indexType,
            Collection<String> uniformNames,
            T uniforms,
            CallbackInfo ci
    ) {
        if (draws == null || draws.isEmpty()) {
            ci.cancel();
            return;
        }

        RenderPass.Draw<T> firstDraw = draws.iterator().next();
        if (firstDraw == null) return;
        GpuBuffer vertexBuffer = firstDraw.vertexBuffer();

        // Check if the vertex buffer belongs to our custom Xeno pool
        if (XenoWorldRenderer.isPoolBuffer(vertexBuffer)) {
            ci.cancel();

            RenderPass.UniformUploader uploader = this::setUniform;

            GpuBuffer currentVB = null;
            GpuBuffer currentIB = null;
            IndexType currentIBType = null;

            // Execute draw calls with dynamic buffer binding tracking
            for (RenderPass.Draw<T> draw : draws) {
                if (draw != null) {
                    GpuBuffer vb = draw.vertexBuffer();
                    if (vb != currentVB) {
                        this.setVertexBuffer(draw.slot(), vb.slice());
                        currentVB = vb;
                    }

                    GpuBuffer ib = draw.indexBuffer() != null ? draw.indexBuffer() : indexBuffer;
                    IndexType type = draw.indexType() != null ? draw.indexType() : indexType;

                    if (ib != currentIB || type != currentIBType) {
                        this.setIndexBuffer(ib, type);
                        currentIB = ib;
                        currentIBType = type;
                    }

                    if (draw.uniformUploaderConsumer() != null) {
                        draw.uniformUploaderConsumer().accept(uniforms, uploader);
                    }
                    this.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
                }
            }
        }
    }
}
