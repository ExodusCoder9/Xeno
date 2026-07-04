package com.xeno.client.render.pipeline;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.xeno.client.render.chunk.terrain.ChunkRenderList;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public class RenderPassManager {
	public void buildTerrainPasses(
		FrameGraphBuilder frameGraph,
		RenderTarget mainTarget,
		GpuTextureView depthTexture,
		ChunkRenderList solidList,
		ChunkRenderList cutoutList,
		ChunkRenderList translucentList
	) {
		FramePass solidPass = frameGraph.addPass("xeno_solid");
		solidPass.executes(() -> {
			// Solid terrain rendering
		});

		FramePass cutoutPass = frameGraph.addPass("xeno_cutout");
		cutoutPass.executes(() -> {
			// Cutout terrain rendering
		});

		FramePass translucentPass = frameGraph.addPass("xeno_translucent");
		translucentPass.executes(() -> {
			// Translucent terrain rendering
		});
	}
}
