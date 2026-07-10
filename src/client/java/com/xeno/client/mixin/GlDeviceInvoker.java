package com.xeno.client.mixin;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface GlDeviceInvoker {
    @Invoker("getOrCompilePipeline")
    GlRenderPipeline invokeGetOrCompilePipeline(RenderPipeline pipeline);
}
