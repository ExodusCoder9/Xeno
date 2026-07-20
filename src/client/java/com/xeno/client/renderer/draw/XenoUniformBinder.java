package com.xeno.client.renderer.draw;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.function.BiConsumer;

/**
 * Mutable uniform binder that implements BiConsumer to avoid lambda allocation overhead
 * during draw list compilation.
 */
public class XenoUniformBinder implements BiConsumer<GpuBufferSlice[], RenderPass.UniformUploader> {
    public int uboIndex = -1;

    @Override
    public void accept(GpuBufferSlice[] sectionUbos, RenderPass.UniformUploader uploader) {
        if (this.uboIndex >= 0 && this.uboIndex < sectionUbos.length) {
            uploader.upload("ChunkSection", sectionUbos[this.uboIndex]);
        }
    }
}
