package com.xeno.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.xeno.client.render.shader.uniforms.UniformManager;
import com.xeno.client.render.chunk.info.SectionInfoBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.HashMap;
import java.util.Map;

public class ShaderManager {
    private final Map<ChunkSectionLayer, RenderPipeline> terrainPipelines;
    private final UniformManager uniformManager;
    private final SectionInfoBuffer sectionInfoBuffer;

    public ShaderManager(GpuDevice device) {
        this.terrainPipelines = new HashMap<>();
        terrainPipelines.put(ChunkSectionLayer.SOLID, RenderPipelines.SOLID_TERRAIN);
        terrainPipelines.put(ChunkSectionLayer.CUTOUT, RenderPipelines.CUTOUT_TERRAIN);
        terrainPipelines.put(ChunkSectionLayer.TRANSLUCENT, RenderPipelines.TRANSLUCENT_TERRAIN);
        this.uniformManager = new UniformManager(device);
        this.sectionInfoBuffer = new SectionInfoBuffer(device, 32768);
    }

    public void bindFrameUniforms(RenderPass pass) {
        uniformManager.bindAll(pass);
        pass.setUniform("SectionInfo", sectionInfoBuffer.getBufferSlice());
    }

    public void bindChunkUniforms(RenderPass pass, int regionIndex) {
        pass.setUniform("ChunkOffsets", uniformManager.getChunkUniforms().getSlice(regionIndex));
    }

    public UniformManager getUniformManager() { return uniformManager; }

    public RenderPipeline getTerrainPipeline(ChunkSectionLayer layer) {
        return terrainPipelines.get(layer);
    }
}
