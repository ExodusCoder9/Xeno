package com.xeno.client.render.pipeline;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.HashMap;
import java.util.Map;

public class RenderPipelineManager {
	private final Map<TerrainRenderPass, RenderPipeline> pipelines;

	public RenderPipelineManager(GpuDevice device) {
		pipelines = new HashMap<>();
		pipelines.put(TerrainRenderPass.SOLID, RenderPipelines.SOLID_TERRAIN);
		pipelines.put(TerrainRenderPass.CUTOUT, RenderPipelines.CUTOUT_TERRAIN);
		pipelines.put(TerrainRenderPass.TRANSLUCENT, RenderPipelines.TRANSLUCENT_TERRAIN);
	}

	public RenderPipeline getPipeline(TerrainRenderPass pass) {
		return pipelines.get(pass);
	}

	public void setPipeline(TerrainRenderPass pass, RenderPipeline pipeline) {
		pipelines.put(pass, pipeline);
	}

	public void close() {
		pipelines.clear();
	}
}
