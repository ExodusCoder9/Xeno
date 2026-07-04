package com.xeno.client.render.pipeline;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import com.mojang.blaze3d.pipeline.RenderPipeline;

public class TerrainRenderPass {
	public final ChunkSectionLayer layer;
	public RenderPipeline pipeline;
	public final boolean translucent;
	public final boolean depthTestWrite;

	public TerrainRenderPass(ChunkSectionLayer layer, RenderPipeline pipeline, boolean translucent, boolean depthTestWrite) {
		this.layer = layer;
		this.pipeline = pipeline;
		this.translucent = translucent;
		this.depthTestWrite = depthTestWrite;
	}

	public static final TerrainRenderPass SOLID = new TerrainRenderPass(
		ChunkSectionLayer.SOLID, null, false, true
	);
	public static final TerrainRenderPass CUTOUT = new TerrainRenderPass(
		ChunkSectionLayer.CUTOUT, null, false, true
	);
	public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass(
		ChunkSectionLayer.TRANSLUCENT, null, true, false
	);
}
