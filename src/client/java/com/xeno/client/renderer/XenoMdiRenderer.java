package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.IndexType;
import java.util.List;

public class XenoMdiRenderer {

    public static void renderDirect(
        RenderPass renderPass,
        List<RenderPass.Draw<GpuBufferSlice[]>> draws,
        GpuBuffer defaultIndexBuffer,
        IndexType defaultIndexType,
        GpuBufferSlice[] chunkSectionInfos
    ) {
        GpuBuffer lastIndexBuffer = null;
        IndexType lastIndexType = null;
        GpuBuffer lastVertexBuffer = null;

        for (RenderPass.Draw<GpuBufferSlice[]> draw : draws) {
            GpuBuffer ib = draw.indexBuffer() != null ? draw.indexBuffer() : defaultIndexBuffer;
            IndexType it = draw.indexType() != null ? draw.indexType() : defaultIndexType;
            if (ib != lastIndexBuffer || it != lastIndexType) {
                renderPass.setIndexBuffer(ib, it);
                lastIndexBuffer = ib;
                lastIndexType = it;
            }

            GpuBuffer vb = draw.vertexBuffer();
            if (vb != lastVertexBuffer) {
                renderPass.setVertexBuffer(0, vb.slice());
                lastVertexBuffer = vb;
            }

            renderPass.setUniform("ChunkSection", chunkSectionInfos[draw.slot()]);
            renderPass.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }
}
