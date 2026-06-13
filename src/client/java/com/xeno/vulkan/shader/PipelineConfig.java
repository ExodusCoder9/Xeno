package com.xeno.vulkan.shader;

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


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.util.GsonHelper;
import com.xeno.vulkan.texture.VTextureSelector;

public class PipelineConfig {
   public final EnumMap<SPIRVUtils.ShaderKind, String> shaderPaths;
   public final List<PipelineConfig.UB> ubs;
   public final List<PipelineConfig.ImageDescriptorInfo> imageDescriptors;
   public final PipelineConfig.UB pushConstantsInfo;

   public PipelineConfig(
      EnumMap<SPIRVUtils.ShaderKind, String> shaderPaths,
      List<PipelineConfig.UB> ubs,
      List<PipelineConfig.ImageDescriptorInfo> imageDescriptors,
      PipelineConfig.UB pushConstantsInfo
   ) {
      this.shaderPaths = shaderPaths;
      this.ubs = ubs;
      this.imageDescriptors = imageDescriptors;
      this.pushConstantsInfo = pushConstantsInfo;
   }

   public static PipelineConfig fromJson(String configName, JsonObject config) {
      PipelineConfig.Builder builder = builder();
      String defaultPath = String.format("%s", configName, configName);
      String vertexShader = config.has("vertex") ? config.get("vertex").getAsString() : defaultPath;
      String fragmentShader = config.has("fragment") ? config.get("fragment").getAsString() : defaultPath;
      builder.withShader(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexShader);
      builder.withShader(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentShader);
      JsonArray jsonUbos = GsonHelper.getAsJsonArray(config, "UBOs", null);
      JsonArray jsonManualUbos = GsonHelper.getAsJsonArray(config, "ManualUBOs", null);
      JsonArray jsonSamplers = GsonHelper.getAsJsonArray(config, "samplers", null);
      JsonArray jsonPushConstants = GsonHelper.getAsJsonArray(config, "PushConstants", null);
      int nextBinding = 0;
      if (jsonUbos != null) {
         for (JsonElement jsonelement : jsonUbos) {
            PipelineConfig.UB ub = parseUboNode(jsonelement);
            builder.addUB(ub);
            nextBinding = ub.binding + 1;
         }
      }

      if (jsonManualUbos != null) {
         PipelineConfig.UB ub = parseManualUboNode(jsonManualUbos.get(0));
         builder.addUB(ub);
      }

      if (jsonSamplers != null) {
         for (JsonElement jsonelement : jsonSamplers) {
            PipelineConfig.ImageDescriptorInfo imageDescriptor = parseSamplerNode(jsonelement, nextBinding);
            builder.addImageDescriptor(imageDescriptor);
            nextBinding++;
         }
      }

      if (jsonPushConstants != null) {
         PipelineConfig.UB ub = parsePushConstantNode(jsonPushConstants);
         builder.setPushConstants(ub);
      }

      return builder.build();
   }

   private static PipelineConfig.UB parseUboNode(JsonElement jsonelement) {
      JsonObject uboJson = GsonHelper.convertToJsonObject(jsonelement, "UBO");
      int binding = GsonHelper.getAsInt(uboJson, "binding");
      int stages = getStageFromString(GsonHelper.getAsString(uboJson, "type"));
      PipelineConfig.UB.Builder builder = PipelineConfig.UB.builder(binding, stages);
      if (GsonHelper.isArrayNode(uboJson, "fields")) {
         for (JsonElement field : GsonHelper.getAsJsonArray(uboJson, "fields")) {
            JsonObject fieldObject = GsonHelper.convertToJsonObject(field, "uniform");
            String name = GsonHelper.getAsString(fieldObject, "name");
            String type2 = GsonHelper.getAsString(fieldObject, "type");
            int count = GsonHelper.getAsInt(fieldObject, "count");
            type2 = convertType(type2, count);
            builder.addUniform(type2, name);
         }
      } else {
         int size = GsonHelper.getAsInt(uboJson, "size");
         builder.setSize(size);
      }

      return builder.build();
   }

   private static PipelineConfig.UB parseManualUboNode(JsonElement jsonelement) {
      JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "ManualUBO");
      int binding = GsonHelper.getAsInt(jsonobject, "binding");
      int stages = getStageFromString(GsonHelper.getAsString(jsonobject, "type"));
      int size = GsonHelper.getAsInt(jsonobject, "size");
      PipelineConfig.UB.Builder builder = PipelineConfig.UB.builder(binding, stages);
      builder.setSize(size);
      return builder.build();
   }

   private static PipelineConfig.ImageDescriptorInfo parseSamplerNode(JsonElement jsonelement, int binding) {
      JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "Sampler");
      String name = GsonHelper.getAsString(jsonobject, "name");
      int imageIdx = VTextureSelector.getTextureIdx(name);
      return new PipelineConfig.ImageDescriptorInfo(binding, "sampler2D", name, imageIdx);
   }

   private static PipelineConfig.UB parsePushConstantNode(JsonArray jsonArray) {
      PipelineConfig.UB.Builder builder = PipelineConfig.UB.builder(0, 1);

      for (JsonElement field : jsonArray) {
         JsonObject fieldObject = GsonHelper.convertToJsonObject(field, "PushConstants");
         String name = GsonHelper.getAsString(fieldObject, "name");
         String type2 = GsonHelper.getAsString(fieldObject, "type");
         int count = GsonHelper.getAsInt(fieldObject, "count");
         type2 = convertType(type2, count);
         builder.addUniform(type2, name);
      }

      return builder.build();
   }

   public static PipelineConfig.Builder builder() {
      return new PipelineConfig.Builder();
   }

   public static int getStageFromString(String s) {
      return switch (s) {
         case "vertex" -> 1;
         case "fragment" -> 16;
         case "all" -> 31;
         case "compute" -> 32;
         default -> throw new RuntimeException("cannot identify type..");
      };
   }

   public static String convertType(String type, int count) {
      return switch (type) {
         case "matrix4x4" -> "mat4";
         case "float" -> {
            switch (count) {
               case 1:
                  yield "float";
               case 2:
                  yield "vec2";
               case 3:
                  yield "vec3";
               case 4:
                  yield "vec4";
               default:
                  throw new IllegalStateException("Unexpected value: " + count);
            }
         }
         case "int" -> {
            switch (count) {
               case 1:
                  yield "int";
               case 2:
                  yield "ivec2";
               case 3:
                  yield "ivec3";
               case 4:
                  yield "ivec4";
               default:
                  throw new IllegalStateException("Unexpected value: " + count);
            }
         }
         default -> throw new RuntimeException("not admitted type..");
      };
   }

   public static class Builder {
      EnumMap<SPIRVUtils.ShaderKind, String> shaderPaths = new EnumMap<>(SPIRVUtils.ShaderKind.class);
      public List<PipelineConfig.UB> ubs = new ArrayList<>();
      public List<PipelineConfig.ImageDescriptorInfo> imageDescriptors = new ArrayList<>();
      public PipelineConfig.UB pushConstantsInfo;

      public Builder() {
      }

      public PipelineConfig.Builder withShader(SPIRVUtils.ShaderKind shaderKind, String path) {
         this.shaderPaths.put(shaderKind, path);
         return this;
      }

      public PipelineConfig.Builder addUB(PipelineConfig.UB ub) {
         this.ubs.add(ub);
         return this;
      }

      public PipelineConfig.Builder addImageDescriptor(int binding, String type, String name, int imageIdx) {
         this.imageDescriptors.add(new PipelineConfig.ImageDescriptorInfo(binding, type, name, imageIdx));
         return this;
      }

      public PipelineConfig.Builder addImageDescriptor(PipelineConfig.ImageDescriptorInfo info) {
         this.imageDescriptors.add(info);
         return this;
      }

      public PipelineConfig.Builder setPushConstants(PipelineConfig.UB pc) {
         this.pushConstantsInfo = pc;
         return this;
      }

      public PipelineConfig build() {
         return new PipelineConfig(this.shaderPaths, this.ubs, this.imageDescriptors, this.pushConstantsInfo);
      }
   }

   public interface DescriptorBinding {
      int getBinding();
   }

   public record ImageDescriptorInfo(int binding, String type, String name, int imageIdx) {
      public ImageDescriptorInfo {
      }
   }

   public static class UB {
      public final int binding;
      public final int stage;
      public final int size;
      public final List<PipelineConfig.Uniform> uniforms;

      public static PipelineConfig.UB.Builder builder(int binding, int stage) {
         return new PipelineConfig.UB.Builder(binding, stage);
      }

      public UB(int binding, int stage, int size, List<PipelineConfig.Uniform> uniforms) {
         this.binding = binding;
         this.stage = stage;
         this.size = size;
         this.uniforms = uniforms;
      }

      public static class Builder {
         int binding;
         int stage;
         int size;
         List<PipelineConfig.Uniform> uniforms = new ArrayList<>();

         public Builder(int binding, int stage) {
            this.binding = binding;
            this.stage = stage;
         }

         public PipelineConfig.UB.Builder addUniform(String type, String name) {
            this.uniforms.add(new PipelineConfig.Uniform(type, name));
            return this;
         }

         public PipelineConfig.UB.Builder setSize(int size) {
            this.size = size;
            return this;
         }

         public PipelineConfig.UB build() {
            return new PipelineConfig.UB(this.binding, this.stage, this.size, this.uniforms);
         }
      }
   }

   public record Uniform(String type, String name) {
      public Uniform {
      }
   }
}
