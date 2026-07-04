package com.xeno.client.render.sky;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.buffers.GpuBufferSlice;

public class SunriseSunsetRenderer implements AutoCloseable {
	public void addPass(FrameGraphBuilder frame, RenderTarget mainTarget, net.minecraft.client.renderer.state.level.SkyRenderState state, GpuBufferSlice skyFog) {}
	@Override public void close() {}
}
