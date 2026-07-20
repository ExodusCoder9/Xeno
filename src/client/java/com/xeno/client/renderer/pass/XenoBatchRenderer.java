package com.xeno.client.renderer.pass;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.xeno.client.renderer.XenoWorldRenderer;

import java.util.Collection;

/**
 * Custom batch renderer that optimizes multi-draw indexed buffer bindings and uniform uploads.
 */
public class XenoBatchRenderer {

    @FunctionalInterface
    public interface VertexBufferSetter {
        void set(int slot, GpuBufferSlice slice);
    }

    @FunctionalInterface
    public interface IndexBufferSetter {
        void set(GpuBuffer buffer, IndexType type);
    }

    @FunctionalInterface
    public interface UniformUploaderSetter {
        void set(String name, GpuBufferSlice value);
    }

    @FunctionalInterface
    public interface DrawIndexedExecutor {
        void draw(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance);
    }

    public static <T> boolean drawMultipleIndexed(
            Collection<RenderPass.Draw<T>> draws,
            GpuBuffer defaultIndexBuffer,
            IndexType defaultIndexType,
            T uniformArgument,
            VertexBufferSetter vbSetter,
            IndexBufferSetter ibSetter,
            UniformUploaderSetter uploaderSetter,
            DrawIndexedExecutor drawExecutor
    ) {
        if (draws == null || draws.isEmpty()) {
            return false;
        }

        RenderPass.Draw<T> firstDraw = draws.iterator().next();
        if (firstDraw == null) return false;
        GpuBuffer vertexBuffer = firstDraw.vertexBuffer();

        // Check if the vertex buffer belongs to our custom Xeno pool
        if (XenoWorldRenderer.isPoolBuffer(vertexBuffer)) {
            RenderPass.UniformUploader uploader = uploaderSetter::set;

            GpuBuffer currentVB = null;
            GpuBuffer currentIB = null;
            IndexType currentIBType = null;

            for (RenderPass.Draw<T> draw : draws) {
                if (draw != null) {
                    GpuBuffer vb = draw.vertexBuffer();
                    if (vb != currentVB) {
                        vbSetter.set(draw.slot(), vb.slice());
                        currentVB = vb;
                    }

                    GpuBuffer ib = draw.indexBuffer() != null ? draw.indexBuffer() : defaultIndexBuffer;
                    IndexType type = draw.indexType() != null ? draw.indexType() : defaultIndexType;

                    if (ib != currentIB || type != currentIBType) {
                        ibSetter.set(ib, type);
                        currentIB = ib;
                        currentIBType = type;
                    }

                    if (draw.uniformUploaderConsumer() != null) {
                        draw.uniformUploaderConsumer().accept(uniformArgument, uploader);
                    }
                    drawExecutor.draw(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
                }
            }
            return true;
        }
        return false;
    }
}
