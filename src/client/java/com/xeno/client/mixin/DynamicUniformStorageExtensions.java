package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.util.List;

public interface DynamicUniformStorageExtensions<T> {
    GpuBufferSlice[] xenoWriteUniforms(List<T> uniforms);
}
