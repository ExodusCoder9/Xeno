package com.xeno.render.chunk.build.thread;

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


import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import com.xeno.render.shader.PipelineManager;
import com.xeno.render.vertex.CustomVertexFormat;
import com.xeno.render.vertex.TerrainBuilder;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.render.vertex.VertexBuilder;

public class ThreadBuilderPack {
   private static Function<TerrainRenderType, TerrainBuilder> terrainBuilderConstructor;
   private final TerrainBuilder[] builders;

   public static void defaultTerrainBuilderConstructor() {
      terrainBuilderConstructor = renderType -> {
         int size = TerrainRenderType.getLayer(renderType).bufferSize() / DefaultVertexFormat.BLOCK.getVertexSize();
         boolean compressedFormat = PipelineManager.terrainVertexFormat == CustomVertexFormat.COMPRESSED_TERRAIN;
         VertexBuilder vertexBuilder = compressedFormat ? new VertexBuilder.CompressedVertexBuilder() : new VertexBuilder.DefaultVertexBuilder();
         return new TerrainBuilder(size, vertexBuilder);
      };
   }

   public static void setTerrainBuilderConstructor(Function<TerrainRenderType, TerrainBuilder> constructor) {
      terrainBuilderConstructor = constructor;
   }

   public ThreadBuilderPack() {
      this.builders = new TerrainBuilder[TerrainRenderType.VALUES.length];
      for (TerrainRenderType type : TerrainRenderType.VALUES) {
         this.builders[type.ordinal()] = terrainBuilderConstructor.apply(type);
      }
   }

   public TerrainBuilder builder(TerrainRenderType renderType) {
      return this.builders[renderType.ordinal()];
   }

   public void replaceBuilder(TerrainRenderType renderType, TerrainBuilder builder) {
      this.builders[renderType.ordinal()] = builder;
   }

   public void freeAll() {
      for (TerrainBuilder builder : this.builders) {
         if (builder != null) {
            builder.free();
         }
      }
   }
}
