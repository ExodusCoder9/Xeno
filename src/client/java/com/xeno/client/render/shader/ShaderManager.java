package com.xeno.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.xeno.client.render.shader.uniforms.UniformBuffer;
import com.xeno.client.render.chunk.info.SectionInfoBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.HashMap;
import java.util.Map;

public class ShaderManager {
	private final Map<ChunkSectionLayer, RenderPipeline> terrainPipelines;
	private final UniformBuffer modelViewBuffer;
	private final UniformBuffer projectionBuffer;
	private final UniformBuffer chunkOffsetBuffer;
	private final UniformBuffer fogParamsBuffer;
	private final UniformBuffer lightParamsBuffer;
	private final SectionInfoBuffer sectionInfoBuffer;

	public ShaderManager(GpuDevice device) {
		this.terrainPipelines = new HashMap<>();
		terrainPipelines.put(ChunkSectionLayer.SOLID, RenderPipelines.SOLID_TERRAIN);
		terrainPipelines.put(ChunkSectionLayer.CUTOUT, RenderPipelines.CUTOUT_TERRAIN);
		terrainPipelines.put(ChunkSectionLayer.TRANSLUCENT, RenderPipelines.TRANSLUCENT_TERRAIN);
		this.modelViewBuffer = new UniformBuffer(device, 64, 65536);
		this.projectionBuffer = new UniformBuffer(device, 64, 65536);
		this.chunkOffsetBuffer = new UniformBuffer(device, 16, 256);
		this.fogParamsBuffer = new UniformBuffer(device, 48, 65536);
		this.lightParamsBuffer = new UniformBuffer(device, 32, 65536);
		this.sectionInfoBuffer = new SectionInfoBuffer(device, 32768);
	}

	public void bindFrameUniforms(RenderPass pass) {
		pass.setUniform("ModelViewMat", modelViewBuffer.getSlice());
		pass.setUniform("ProjectionMat", projectionBuffer.getSlice());
		pass.setUniform("FogParams", fogParamsBuffer.getSlice());
		pass.setUniform("LightParams", lightParamsBuffer.getSlice());
		pass.setUniform("SectionInfo", sectionInfoBuffer.getBufferSlice());
	}

	public void bindChunkUniforms(RenderPass pass, int regionIndex) {
		pass.setUniform("ChunkOffsets", chunkOffsetBuffer.getSlice(regionIndex));
	}

	public RenderPipeline getTerrainPipeline(ChunkSectionLayer layer) {
		return terrainPipelines.get(layer);
	}
}
