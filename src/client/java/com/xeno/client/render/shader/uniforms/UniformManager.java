package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;

public class UniformManager {
    private final ChunkUniforms chunkUniforms;
    private final CloudUniforms cloudUniforms;
    private final EntityUniforms entityUniforms;
    private final FogUniforms fogUniforms;
    private final LightUniforms lightUniforms;
    private final ModelViewUniforms modelViewUniforms;
    private final ProjectionUniforms projectionUniforms;
    private final SkyUniforms skyUniforms;
    private final WeatherUniforms weatherUniforms;

    public UniformManager(GpuDevice device) {
        this.chunkUniforms = new ChunkUniforms(device);
        this.cloudUniforms = new CloudUniforms(device);
        this.entityUniforms = new EntityUniforms(device);
        this.fogUniforms = new FogUniforms(device);
        this.lightUniforms = new LightUniforms(device);
        this.modelViewUniforms = new ModelViewUniforms(device);
        this.projectionUniforms = new ProjectionUniforms(device);
        this.skyUniforms = new SkyUniforms(device);
        this.weatherUniforms = new WeatherUniforms(device);
    }

    public ChunkUniforms getChunkUniforms() { return chunkUniforms; }
    public CloudUniforms getCloudUniforms() { return cloudUniforms; }
    public EntityUniforms getEntityUniforms() { return entityUniforms; }
    public FogUniforms getFogUniforms() { return fogUniforms; }
    public LightUniforms getLightUniforms() { return lightUniforms; }
    public ModelViewUniforms getModelViewUniforms() { return modelViewUniforms; }
    public ProjectionUniforms getProjectionUniforms() { return projectionUniforms; }
    public SkyUniforms getSkyUniforms() { return skyUniforms; }
    public WeatherUniforms getWeatherUniforms() { return weatherUniforms; }

    public void bindAll(RenderPass pass) {
        chunkUniforms.bind(pass);
        cloudUniforms.bind(pass);
        entityUniforms.bind(pass);
        fogUniforms.bind(pass);
        lightUniforms.bind(pass);
        modelViewUniforms.bind(pass);
        projectionUniforms.bind(pass);
        skyUniforms.bind(pass);
        weatherUniforms.bind(pass);
    }

    public void close() {
        chunkUniforms.close();
        cloudUniforms.close();
        entityUniforms.close();
        fogUniforms.close();
        lightUniforms.close();
        modelViewUniforms.close();
        projectionUniforms.close();
        skyUniforms.close();
        weatherUniforms.close();
    }
}
