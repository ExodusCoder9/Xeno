package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.function.BiConsumer;

public class XenoUploader implements BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader> {
    public int uboIndex;

    public XenoUploader(int uboIndex) {
        this.uboIndex = uboIndex;
    }

    @Override
    public void accept(GpuBufferSlice[] sectionUbos, RenderPass.UniformUploader uploader) {
        uploader.upload("ChunkSection", sectionUbos[uboIndex]);
    }
}
