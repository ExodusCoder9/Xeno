package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.IndexType;
import net.minecraft.client.renderer.DynamicUniforms;
import java.nio.ByteBuffer;
import java.util.List;

public class XenoMdiRenderer {
    public static final ThreadLocal<List<DynamicUniforms.ChunkSectionInfo>> CURRENT_SECTION_INFOS = new ThreadLocal<>();

    private static GpuBuffer indirectBuffer;
    private static GpuBuffer uniformBuffer;

    private static final int MAX_SECTIONS = 1024;
    private static final int COMMAND_SIZE = 20; // VkDrawIndexedIndirectCommand.SIZEOF
    private static final int UNIFORM_ELEMENT_SIZE = 32; // std140 size of SectionData

    private static void ensureCapacity() {
        if (indirectBuffer == null || indirectBuffer.isClosed()) {
            indirectBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Xeno MDI Commands Buffer",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_INDIRECT_PARAMETERS,
                MAX_SECTIONS * COMMAND_SIZE
            );
        }
        if (uniformBuffer == null || uniformBuffer.isClosed()) {
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Xeno MDI Uniforms Buffer",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM,
                64 + MAX_SECTIONS * UNIFORM_ELEMENT_SIZE
            );
        }
    }

    public static boolean tryRenderMdi(
        RenderPass renderPass,
        List<RenderPass.Draw<GpuBufferSlice[]>> draws,
        GpuBuffer defaultIndexBuffer,
        IndexType defaultIndexType
    ) {
        if (draws.isEmpty()) return true;

        List<DynamicUniforms.ChunkSectionInfo> sectionInfos = CURRENT_SECTION_INFOS.get();
        if (sectionInfos == null) {
            return false;
        }

        // Check hardware features
        var features = RenderSystem.getDevice().getDeviceInfo().features();
        if (!features.drawIndirect() || !features.multiDrawIndirect()) {
            return false; // Fallback to vanilla drawMultipleIndexed path
        }

        int drawCount = draws.size();
        if (drawCount > MAX_SECTIONS) {
            drawCount = MAX_SECTIONS; // Cap at max supported sections per draw call
        }

        ensureCapacity();

        // Map buffers
        GpuBufferSlice indirectSlice = indirectBuffer.slice(0, drawCount * COMMAND_SIZE);
        GpuBufferSlice uniformSlice = uniformBuffer.slice(0, 64 + drawCount * UNIFORM_ELEMENT_SIZE);

        try (GpuBufferSlice.MappedView cmdMap = indirectSlice.map(false, true);
             GpuBufferSlice.MappedView uboMap = uniformSlice.map(false, true)) {

            ByteBuffer cmdData = cmdMap.data();
            ByteBuffer uboData = uboMap.data();

            RenderPass.Draw<GpuBufferSlice[]> firstDraw = draws.getFirst();
            GpuBuffer vertexBuffer = firstDraw.vertexBuffer();
            GpuBuffer indexBuffer = firstDraw.indexBuffer() == null ? defaultIndexBuffer : firstDraw.indexBuffer();
            IndexType indexType = firstDraw.indexType() == null ? defaultIndexType : firstDraw.indexType();

            // 1. Write ModelViewMat once at the beginning (offset 0)
            XenoUploader firstUploader = (XenoUploader) firstDraw.uniformUploaderConsumer();
            int firstUboIndex = firstUploader != null ? firstUploader.uboIndex : 0;
            DynamicUniforms.ChunkSectionInfo firstInfo = sectionInfos.get(firstUboIndex);
            uboData.position(0);
            Std140Builder.intoBuffer(uboData).putMat4f(firstInfo.modelView());

            for (int i = 0; i < drawCount; i++) {
                RenderPass.Draw<GpuBufferSlice[]> draw = draws.get(i);

                // 2. Write MDI command (VkDrawIndexedIndirectCommand)
                int cmdOffset = i * COMMAND_SIZE;
                cmdData.putInt(cmdOffset, draw.indexCount());
                cmdData.putInt(cmdOffset + 4, 1); // instanceCount = 1
                cmdData.putInt(cmdOffset + 8, draw.firstIndex());
                cmdData.putInt(cmdOffset + 12, draw.baseVertex());
                cmdData.putInt(cmdOffset + 16, i); // firstInstance = i (maps to gl_DrawID)

                // 3. Write SectionData to uniform buffer
                XenoUploader uploader = (XenoUploader) draw.uniformUploaderConsumer();
                int uboIndex = uploader != null ? uploader.uboIndex : 0;
                DynamicUniforms.ChunkSectionInfo info = sectionInfos.get(uboIndex);

                uboData.position(64 + i * UNIFORM_ELEMENT_SIZE);
                Std140Builder.intoBuffer(uboData)
                    .putFloat(info.visibility())
                    .putIVec2(info.textureAtlasWidth(), info.textureAtlasHeight())
                    .putIVec3(info.x(), info.y(), info.z());
            }

            // Set bindings once
            renderPass.setIndexBuffer(indexBuffer, indexType);
            renderPass.setVertexBuffer(firstDraw.slot(), vertexBuffer.slice());
            renderPass.setUniform("ChunkSection", uniformSlice);

            // Draw all visible chunks in one single command
            renderPass.drawIndexedIndirect(indirectSlice, drawCount);
        }

        return true;
    }
}
