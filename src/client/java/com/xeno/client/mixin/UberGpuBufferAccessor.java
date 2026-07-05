package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.UberGpuBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UberGpuBuffer.class)
public interface UberGpuBufferAccessor {
    @Accessor("bufferUsage")
    int getBufferUsage();
}
