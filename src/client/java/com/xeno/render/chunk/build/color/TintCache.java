package com.xeno.render.chunk.build.color;

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


import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import com.xeno.render.chunk.build.biome.BiomeData;

public class TintCache {
   private static final int SECTION_WIDTH = 16;
   private static final int BOUNDARY_WIDTH = 16;
   private static final int LAYER_COUNT = 48;
   private final Reference2ReferenceOpenHashMap<ColorResolver, TintCache.Layer[]> layers = new Reference2ReferenceOpenHashMap();
   private BiomeData biomeData;
   private int blendRadius;
   private int totalWidth;
   private int secX;
   private int secY;
   private int secZ;
   private int minX;
   private int minY;
   private int minZ;
   private int maxX;
   private int maxY;
   private int maxZ;
   private int dataSize;
   private int[] temp;
   private ColorResolver lastResolver;
   private TintCache.Layer[] lastLayersArr;
   private int lastX = Integer.MIN_VALUE;
   private int lastY = Integer.MIN_VALUE;
   private int lastZ = Integer.MIN_VALUE;
   private ColorResolver lastRequestResolver;
   private int lastColor;

   public TintCache() {
      this.layers.put(BiomeColors.FOLIAGE_COLOR_RESOLVER, this.allocateLayers());
      this.layers.put(BiomeColors.GRASS_COLOR_RESOLVER, this.allocateLayers());
      this.layers.put(BiomeColors.WATER_COLOR_RESOLVER, this.allocateLayers());
   }

   public void init(BiomeData biomeData, int blendRadius, int secX, int secY, int secZ) {
      this.biomeData = biomeData;
      this.blendRadius = (Integer)Minecraft.getInstance().options.biomeBlendRadius().get();
      this.totalWidth = blendRadius * 2 + 16;
      this.secX = secX;
      this.secY = secY;
      this.secZ = secZ;
      this.minX = (secX << 4) - blendRadius;
      this.minZ = (secZ << 4) - blendRadius;
      this.maxX = (secX << 4) + 15 + blendRadius;
      this.maxZ = (secZ << 4) + 15 + blendRadius;
      this.minY = (secY << 4) - 2;
      this.maxY = this.minY + 15 + 4;
      this.lastX = Integer.MIN_VALUE;
      this.lastY = Integer.MIN_VALUE;
      this.lastZ = Integer.MIN_VALUE;
      this.lastRequestResolver = null;
      this.lastColor = 0;
      int size = this.totalWidth * this.totalWidth;
      if (size != this.dataSize) {
         this.dataSize = size;
         ObjectIterator var7 = this.layers.values().iterator();

         while (var7.hasNext()) {
            TintCache.Layer[] layers = (TintCache.Layer[])var7.next();

            for (TintCache.Layer layer : layers) {
               layer.allocate(size);
            }
         }

         this.temp = new int[size];
      } else {
         ObjectIterator var13 = this.layers.values().iterator();

         while (var13.hasNext()) {
            TintCache.Layer[] layers = (TintCache.Layer[])var13.next();

            for (TintCache.Layer layer : layers) {
               layer.invalidate();
            }
         }
      }
   }

   public int getColor(BlockPos blockPos, ColorResolver colorResolver) {
      int x = blockPos.getX();
      int y = blockPos.getY();
      int z = blockPos.getZ();
      if (x == this.lastX && y == this.lastY && z == this.lastZ && colorResolver == this.lastRequestResolver) {
         return this.lastColor;
      }
      this.lastX = x;
      this.lastY = y;
      this.lastZ = z;
      this.lastRequestResolver = colorResolver;

      int relY = y - this.minY;
      TintCache.Layer[] layersArr = this.lastResolver == colorResolver ? this.lastLayersArr : null;
      if (layersArr == null) {
         layersArr = this.layers.get(colorResolver);
         if (layersArr == null) {
            this.addResolver(colorResolver);
            layersArr = this.layers.get(colorResolver);
         }
         this.lastResolver = colorResolver;
         this.lastLayersArr = layersArr;
      }

      TintCache.Layer layer = layersArr[relY];
      if (layer.invalidated) {
         this.calculateLayer(layer, colorResolver, relY);
      }

      int[] values = layer.values;
      int relX = x - this.minX;
      int relZ = z - this.minZ;
      int idx = this.totalWidth * relZ + relX;
      int color = values[idx];
      this.lastColor = color;
      return color;
   }

   private void addResolver(ColorResolver colorResolver) {
      TintCache.Layer[] layers1 = this.allocateLayers();

      for (TintCache.Layer layer : layers1) {
         layer.allocate(this.dataSize);
      }

      this.layers.put(colorResolver, layers1);
   }

   private TintCache.Layer[] allocateLayers() {
      TintCache.Layer[] layers = new TintCache.Layer[48];

      for (int i = 0; i < 48; i++) {
         layers[i] = new TintCache.Layer();
      }

      return layers;
   }

   private void calculateLayer(TintCache.Layer layer, ColorResolver colorResolver, int y) {
      int absY = this.minY + y;
      int[] values = layer.values;

      for (int absZ = this.minZ; absZ <= this.maxZ; absZ++) {
         for (int absX = this.minX; absX <= this.maxX; absX++) {
            Biome biome = this.biomeData.getBiome(absX, absY, absZ);
            int idx = absX - this.minX + (absZ - this.minZ) * this.totalWidth;
            values[idx] = colorResolver.getColor(biome, absX, absZ);
         }
      }

      if (this.blendRadius > 0) {
         this.applyBlur(values);
      }

      layer.invalidated = false;
   }

   private void applyBlur(int[] buffer) {
      int value = buffer[0];
      boolean needsBlur = false;

      for (int i = 1; i < buffer.length; i++) {
         if (value != buffer[i]) {
            needsBlur = true;
            break;
         }
      }

      if (needsBlur) {
         BoxBlur.blur(buffer, this.temp, 16, this.blendRadius);
      }
   }

   static class Layer {
      private boolean invalidated = true;
      private int[] values;

      Layer() {
      }

      void allocate(int size) {
         this.values = new int[size];
         this.invalidate();
      }

      void invalidate() {
         this.invalidated = true;
      }

      public int[] getValues() {
         return this.values;
      }
   }
}
