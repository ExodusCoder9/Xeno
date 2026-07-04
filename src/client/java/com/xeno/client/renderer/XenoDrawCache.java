package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.util.concurrent.ConcurrentHashMap;

public class XenoDrawCache {
    private static final ThreadLocal<XenoDrawKey> QUERY_KEY = ThreadLocal.withInitial(XenoDrawKey::new);
    private static final ConcurrentHashMap<XenoDrawKey, RenderPass.Draw<GpuBufferSlice[]>> CACHE = new ConcurrentHashMap<>();

    public static RenderPass.Draw<GpuBufferSlice[]> getOrCreate(
        GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType,
        int firstIndex, int indexCount, int baseVertex, int uboIndex
    ) {
        XenoDrawKey query = QUERY_KEY.get();
        query.set(vertexBuffer, indexBuffer, indexType, firstIndex, indexCount, baseVertex);

        RenderPass.Draw<GpuBufferSlice[]> draw = CACHE.get(query);
        if (draw != null) {
            // Cache hit: update the mutable UBO index in place with zero allocation
            XenoUploader uploader = (XenoUploader) draw.uniformUploaderConsumer();
            if (uploader != null) {
                uploader.uboIndex = uboIndex;
            }
            return draw;
        }

        // Cache miss: allocate the key and Draw object once
        XenoDrawKey storeKey = new XenoDrawKey(vertexBuffer, indexBuffer, indexType, firstIndex, indexCount, baseVertex);
        XenoUploader uploader = new XenoUploader(uboIndex);
        
        draw = new RenderPass.Draw<>(
            0, vertexBuffer, indexBuffer, indexType, firstIndex, indexCount, baseVertex, uploader
        );
        
        CACHE.put(storeKey, draw);
        return draw;
    }
}
