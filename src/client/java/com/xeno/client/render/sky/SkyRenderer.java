package com.xeno.client.render.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.state.level.SkyRenderState;

public class SkyRenderer {
	private final SkyDiscRenderer discRenderer;
	private final CelestialRenderer celestialRenderer;
	private final SunriseSunsetRenderer sunriseSunsetRenderer;
	private final EndSkyRenderer endSkyRenderer;

	public SkyRenderer() {
		this.discRenderer = new SkyDiscRenderer();
		this.celestialRenderer = new CelestialRenderer();
		this.sunriseSunsetRenderer = new SunriseSunsetRenderer();
		this.endSkyRenderer = new EndSkyRenderer();
	}

	public void addSkyPass(FrameGraphBuilder frame, RenderTarget mainTarget, SkyRenderState state, GpuBufferSlice skyFog) {
		discRenderer.addPass(frame, mainTarget, state, skyFog);
		if (state.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.END) {
			endSkyRenderer.addPass(frame, mainTarget, state, skyFog);
		} else {
			sunriseSunsetRenderer.addPass(frame, mainTarget, state, skyFog);
			celestialRenderer.addPass(frame, mainTarget, state, skyFog);
		}
	}

	public void close() {
		discRenderer.close();
		celestialRenderer.close();
		sunriseSunsetRenderer.close();
		endSkyRenderer.close();
	}
}
