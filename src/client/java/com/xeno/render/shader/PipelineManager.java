package com.xeno.render.shader;

/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */


import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.xeno.render.chunk.build.thread.ThreadBuilderPack;
import com.xeno.render.vertex.CustomVertexFormat;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.shader.GraphicsPipeline;
import com.xeno.vulkan.shader.Pipeline;
import com.xeno.vulkan.shader.PipelineConfig;
import com.xeno.vulkan.shader.SPIRVUtils;
import com.xeno.vulkan.shader.descriptor.UBO;

public abstract class PipelineManager {
   public static VertexFormat terrainVertexFormat;
   static GraphicsPipeline terrainShader;
   static GraphicsPipeline terrainShaderEarlyZ;
   static GraphicsPipeline fastBlitPipeline;
   static GraphicsPipeline cloudsPipeline;
   private static Function<TerrainRenderType, GraphicsPipeline> shaderGetter;

   public PipelineManager() {
   }

   public static void init() {
      setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
      createBasicPipelines();
      setDefaultTerrainShaderGetter();
      ThreadBuilderPack.defaultTerrainBuilderConstructor();
   }

   public static void setDefaultTerrainShaderGetter() {
      setShaderGetter(renderType -> terrainShader);
   }

   private static void createBasicPipelines() {
      terrainShader = createPipeline("terrain", "basic", PipelineConfigs.TERRAIN, CustomVertexFormat.COMPRESSED_TERRAIN);
      terrainShaderEarlyZ = createPipeline("terrain_earlyz", "basic", CustomVertexFormat.COMPRESSED_TERRAIN);
      fastBlitPipeline = createPipeline("blit", "basic/blit", CustomVertexFormat.NONE);
      cloudsPipeline = createPipeline("clouds", "basic/clouds", DefaultVertexFormat.POSITION_COLOR);
   }

   private static GraphicsPipeline createPipeline(String configName, String shaderPath, PipelineConfig config, VertexFormat vertexFormat) {
      Pipeline.Builder pipelineBuilder = new Pipeline.Builder(vertexFormat, configName);
      String path = ShaderLoadUtil.resolveShaderPath(shaderPath);
      pipelineBuilder.applyConfig(config);
      pipelineBuilder.setShaderSrc(
         SPIRVUtils.ShaderKind.VERTEX_SHADER, ShaderLoadUtil.loadShader(path, "%s.vsh".formatted(config.shaderPaths.get(SPIRVUtils.ShaderKind.VERTEX_SHADER)))
      );
      pipelineBuilder.setShaderSrc(
         SPIRVUtils.ShaderKind.FRAGMENT_SHADER,
         ShaderLoadUtil.loadShader(path, "%s.fsh".formatted(config.shaderPaths.get(SPIRVUtils.ShaderKind.FRAGMENT_SHADER)))
      );
      GraphicsPipeline pipeline = pipelineBuilder.createGraphicsPipeline();

      for (UBO buffer : pipeline.getBuffers()) {
         buffer.setUseGlobalBuffer(true);
      }

      return pipeline;
   }

   private static GraphicsPipeline createPipeline(String configName, String shaderPath, VertexFormat vertexFormat) {
      Pipeline.Builder pipelineBuilder = new Pipeline.Builder(vertexFormat, configName);
      String path = ShaderLoadUtil.resolveShaderPath(shaderPath);
      JsonObject config = ShaderLoadUtil.getJsonConfig(path, configName);
      PipelineConfig pipelineConfig = PipelineConfig.fromJson(configName, config);
      pipelineBuilder.applyConfig(pipelineConfig);
      pipelineBuilder.setShaderSrc(
         SPIRVUtils.ShaderKind.VERTEX_SHADER,
         ShaderLoadUtil.loadShader(path, "%s.vsh".formatted(pipelineConfig.shaderPaths.get(SPIRVUtils.ShaderKind.VERTEX_SHADER)))
      );
      pipelineBuilder.setShaderSrc(
         SPIRVUtils.ShaderKind.FRAGMENT_SHADER,
         ShaderLoadUtil.loadShader(path, "%s.fsh".formatted(pipelineConfig.shaderPaths.get(SPIRVUtils.ShaderKind.FRAGMENT_SHADER)))
      );
      GraphicsPipeline pipeline = pipelineBuilder.createGraphicsPipeline();

      for (UBO buffer : pipeline.getBuffers()) {
         buffer.setUseGlobalBuffer(true);
      }

      return pipeline;
   }

   public static GraphicsPipeline getTerrainShader(TerrainRenderType renderType) {
      return shaderGetter.apply(renderType);
   }

   public static void setShaderGetter(Function<TerrainRenderType, GraphicsPipeline> consumer) {
      shaderGetter = consumer;
   }

   public static void setTerrainVertexFormat(VertexFormat format) {
      terrainVertexFormat = format;
   }

   public static VertexFormat getTerrainVertexFormat() {
      return terrainVertexFormat;
   }

   public static GraphicsPipeline getTerrainDirectShader(RenderType renderType) {
      return terrainShader;
   }

   public static GraphicsPipeline getTerrainIndirectShader(RenderType renderType) {
      return terrainShaderEarlyZ;
   }

   public static GraphicsPipeline getFastBlitPipeline() {
      return fastBlitPipeline;
   }

   public static GraphicsPipeline getCloudsPipeline() {
      return cloudsPipeline;
   }

   public static void destroyPipelines() {
      terrainShaderEarlyZ.cleanUp();
      terrainShader.cleanUp();
      fastBlitPipeline.cleanUp();
      cloudsPipeline.cleanUp();
   }
}
