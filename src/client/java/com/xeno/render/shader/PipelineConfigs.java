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


import com.xeno.vulkan.shader.PipelineConfig;
import com.xeno.vulkan.shader.SPIRVUtils;

public class PipelineConfigs {
   public static final PipelineConfig.UB TERRAIN_UB0 = PipelineConfig.UB.builder(0, 1).addUniform("mat4", "MVP").addUniform("int", "CurrentTime").build();
   public static final PipelineConfig.UB TERRAIN_UB1 = PipelineConfig.UB.builder(1, 31)
      .addUniform("vec4", "FogColor")
      .addUniform("float", "FogEnvironmentalStart")
      .addUniform("float", "FogEnvironmentalEnd")
      .addUniform("float", "FogRenderDistanceStart")
      .addUniform("float", "FogRenderDistanceEnd")
      .addUniform("float", "FogSkyEnd")
      .addUniform("float", "FogCloudsEnd")
      .addUniform("float", "AlphaCutout")
      .addUniform("vec2", "TextureSize")
      .addUniform("vec2", "TexelSize")
      .addUniform("int", "UseRgss")
      .build();
   public static final PipelineConfig.UB TERRAIN_UB2 = PipelineConfig.UB.builder(2, 1).setSize(4096).build();
   public static final PipelineConfig.UB TERRAIN_PC = PipelineConfig.UB.builder(0, 1).addUniform("vec3", "ModelOffset").build();
   public static final PipelineConfig TERRAIN = PipelineConfig.builder()
      .withShader(SPIRVUtils.ShaderKind.VERTEX_SHADER, "terrain/terrain")
      .withShader(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, "terrain/terrain")
      .addUB(TERRAIN_UB0)
      .addUB(TERRAIN_UB1)
      .addUB(TERRAIN_UB2)
      .setPushConstants(TERRAIN_PC)
      .addImageDescriptor(3, "sampler2D", "Sampler0", 0)
      .addImageDescriptor(4, "sampler2D", "LightTexture", 2)
      .build();
   static final PipelineConfig TERRAIN_EARLY_Z_CONFIG = PipelineConfig.builder()
      .withShader(SPIRVUtils.ShaderKind.VERTEX_SHADER, "terrain/terrain")
      .withShader(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, "terrain_earlyz/terrain_earlyz")
      .addUB(TERRAIN_UB0)
      .addUB(TERRAIN_UB1)
      .addUB(TERRAIN_UB2)
      .setPushConstants(TERRAIN_PC)
      .addImageDescriptor(3, "sampler2D", "Sampler0", 0)
      .addImageDescriptor(4, "sampler2D", "LightTexture", 2)
      .build();

   public PipelineConfigs() {
   }
}
