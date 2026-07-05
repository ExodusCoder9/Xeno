package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.IndexType;
import net.minecraft.client.renderer.DynamicUniforms;
import java.util.List;

public class XenoMdiRenderer {
    public static final ThreadLocal<List<DynamicUniforms.ChunkSectionInfo>> CURRENT_SECTION_INFOS = new ThreadLocal<>();

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

        int drawCount = draws.size();
        for (int i = 0; i < drawCount; i++) {
            RenderPass.Draw<GpuBufferSlice[]> draw = draws.get(i);

            // 1. Bind Index Buffer (avoid redundant binds)
            GpuBuffer ib = draw.indexBuffer() != null ? draw.indexBuffer() : defaultIndexBuffer;
            IndexType it = draw.indexType() != null ? draw.indexType() : defaultIndexType;
            if (ib != lastIndexBuffer || it != lastIndexType) {
                renderPass.setIndexBuffer(ib, it);
                lastIndexBuffer = ib;
                lastIndexType = it;
            }

            // 2. Bind Vertex Buffer (always slot 0; avoid redundant binds)
            GpuBuffer vb = draw.vertexBuffer();
            if (vb != lastVertexBuffer) {
                renderPass.setVertexBuffer(0, vb.slice());
                lastVertexBuffer = vb;
            }

            // 3. Bind Uniform slice (uboIndex encoded in draw.slot)
            renderPass.setUniform("ChunkSection", chunkSectionInfos[draw.slot()]);

            // 4. Draw
            renderPass.drawIndexed(draw.indexCount(), 1, draw.firstIndex(), draw.baseVertex(), 0);
        }
    }
}
