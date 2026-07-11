package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.util.List;

public interface DynamicUniformStorageExtensions<T> {
    GpuBufferSlice[] xeno$writeUniforms(List<T> uniforms);
}
