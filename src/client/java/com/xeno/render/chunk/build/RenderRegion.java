package com.xeno.render.chunk.build;

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


import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import com.xeno.render.chunk.build.biome.BiomeData;
import com.xeno.render.chunk.build.color.TintCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenderRegion implements BlockAndTintGetter {
   public static final int WIDTH = 3;
   public static final int SIZE = 27;
   public static final int BOUNDARY_BLOCK_WIDTH = 2;
   public static final int REGION_BLOCK_WIDTH = 20;
   public static final int BLOCK_COUNT = 8000;
   public static final BlockState AIR_BLOCK_STATE = Blocks.AIR.defaultBlockState();
   private final int minSecX;
   private final int minSecY;
   private final int minSecZ;
   private final int minX;
   private final int minY;
   private final int minZ;
   private final int maxX;
   private final int maxY;
   private final int maxZ;
   private final Level level;
   private final int blendRadius;
   private final PalettedContainer<BlockState>[] blockDataContainers;
   public BlockState[] blockData;
   private final DataLayer[] lightData;
   private BiomeData biomeData;
   private TintCache tintCache;
   private final Map<BlockPos, BlockEntity> blockEntityMap;
   public final boolean isDebug;

   RenderRegion(
      Level level,
      int x,
      int y,
      int z,
      PalettedContainer<BlockState>[] blockData,
      DataLayer[] lightData,
      BiomeData biomeData,
      Map<BlockPos, BlockEntity> blockEntityMap
   ) {
      this.level = level;
      this.minSecX = x - 1;
      this.minSecY = y - 1;
      this.minSecZ = z - 1;
      this.minX = (this.minSecX << 4) + 16 - 2;
      this.minZ = (this.minSecZ << 4) + 16 - 2;
      this.minY = (this.minSecY << 4) + 16 - 2;
      this.maxX = this.minX + 20;
      this.maxZ = this.minZ + 20;
      this.maxY = this.minY + 20;
      this.blockDataContainers = blockData;
      this.lightData = lightData;
      this.biomeData = biomeData;
      this.blockEntityMap = blockEntityMap;
      this.isDebug = level.isDebug();
      this.blendRadius = (Integer)Minecraft.getInstance().options.biomeBlendRadius().get();
   }

   public void setBlockData(BlockState[] blockData) {
      this.blockData = blockData;
   }

   public void loadBlockStates() {
      Arrays.fill(this.blockData, Blocks.AIR.defaultBlockState());

      for (int x = 0; x <= 2; x++) {
         for (int z = 0; z <= 2; z++) {
            for (int y = 0; y <= 2; y++) {
               int idx = this.getSectionIdx(x, y, z);
               PalettedContainer<BlockState> container = this.blockDataContainers[idx];
               if (container != null) {
                  int absBlockX = x + this.minSecX << 4;
                  int absBlockY = y + this.minSecY << 4;
                  int absBlockZ = z + this.minSecZ << 4;
                  int tMinX = Math.max(this.minX, absBlockX);
                  int tMinY = Math.max(this.minY, absBlockY);
                  int tMinZ = Math.max(this.minZ, absBlockZ);
                  int tMaxX = Math.min(this.maxX, absBlockX + 16);
                  int tMaxY = Math.min(this.maxY, absBlockY + 16);
                  int tMaxZ = Math.min(this.maxZ, absBlockZ + 16);
                  this.loadSectionBlockStates(container, this.blockData, tMinX, tMinY, tMinZ, tMaxX, tMaxY, tMaxZ);
               }
            }
         }
      }
   }

   void loadSectionBlockStates(PalettedContainer<BlockState> container, BlockState[] blockStates, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      for (int y = minY; y < maxY; y++) {
         for (int z = minZ; z < maxZ; z++) {
            for (int x = minX; x < maxX; x++) {
               int idx = this.getBlockIdx(x - this.minX, y - this.minY, z - this.minZ);
               blockStates[idx] = container != null ? (BlockState)container.get(x & 15, y & 15, z & 15) : Blocks.AIR.defaultBlockState();
            }
         }
      }
   }

   public void initTintCache(TintCache tintCache) {
      this.tintCache = tintCache;
      this.tintCache.init(this.biomeData, this.blendRadius, this.minSecX + 1, this.minSecY + 1, this.minSecZ + 1);
   }

   public BlockState getBlockState(BlockPos blockPos) {
      return this.getBlockState(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public BlockState getBlockState(int x, int y, int z) {
      return this.isDebug ? this.debugBlockState(x, y, z) : this.defaultBlockState(x, y, z);
   }

   public FluidState getFluidState(BlockPos blockPos) {
      return this.getBlockState(blockPos).getFluidState();
   }

   public LevelLightEngine getLightEngine() {
      return this.level.getLightEngine();
   }

    public int getBrightness(LightLayer lightLayer, BlockPos blockPos) {
       if (this.outsideRegion(blockPos.getX(), blockPos.getY(), blockPos.getZ())) {
          return 0;
       }

       int secX = SectionPos.blockToSectionCoord(blockPos.getX()) - this.minSecX;
       int secY = SectionPos.blockToSectionCoord(blockPos.getY()) - this.minSecY;
       int secZ = SectionPos.blockToSectionCoord(blockPos.getZ()) - this.minSecZ;
       DataLayer dataLayer = this.lightData[this.getSectionIdx(secX, secY, secZ) << 1 | lightLayer.ordinal()];
       return dataLayer == null ? 0 : dataLayer.get(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15);
    }

    public int getRawBrightness(BlockPos blockPos, int i) {
       if (this.outsideRegion(blockPos.getX(), blockPos.getY(), blockPos.getZ())) {
          return 0;
       }

       int secX = SectionPos.blockToSectionCoord(blockPos.getX()) - this.minSecX;
       int secY = SectionPos.blockToSectionCoord(blockPos.getY()) - this.minSecY;
       int secZ = SectionPos.blockToSectionCoord(blockPos.getZ()) - this.minSecZ;
       int idx = this.getSectionIdx(secX, secY, secZ) << 1;
       DataLayer skyLightLayer = this.lightData[idx | LightLayer.SKY.ordinal()];
       DataLayer blockLightLayer = this.lightData[idx | LightLayer.BLOCK.ordinal()];
       int relX = blockPos.getX() & 15;
       int relY = blockPos.getY() & 15;
       int relZ = blockPos.getZ() & 15;
       int skyLight = skyLightLayer == null ? 0 : skyLightLayer.get(relX, relY, relZ) - i;
       int blockLight = blockLightLayer == null ? 0 : blockLightLayer.get(relX, relY, relZ);
       return Math.max(skyLight, blockLight);
    }

   @Nullable
   public BlockEntity getBlockEntity(@NotNull BlockPos blockPos) {
      return this.blockEntityMap.get(blockPos);
   }

   public CardinalLighting cardinalLighting() {
      return CardinalLighting.DEFAULT;
   }

   public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
      return this.tintCache.getColor(blockPos, colorResolver);
   }

   public int getMinY() {
      return this.level.getMinY();
   }

   public int getHeight() {
      return this.level.getHeight();
   }

   public int getSectionIdx(int secX, int secY, int secZ) {
      return 3 * (3 * secY + secZ) + secX;
   }

   public int getBlockIdx(int x, int y, int z) {
      return 20 * (20 * y + z) + x;
   }

   public boolean outsideRegion(int x, int y, int z) {
      return x < this.minX || x >= this.maxX || y < this.minY || y >= this.maxY || z < this.minZ || z >= this.maxZ;
   }

   public BlockState defaultBlockState(BlockPos blockPos) {
      return this.defaultBlockState(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public BlockState defaultBlockState(int x, int y, int z) {
      x -= this.minX;
      y -= this.minY;
      z -= this.minZ;
      if ((x | y | z | (19 - x) | (19 - y) | (19 - z)) < 0) {
         return AIR_BLOCK_STATE;
      }
      return this.blockData[400 * y + 20 * z + x];
   }

   public BlockState debugBlockState(BlockPos blockPos) {
      return this.debugBlockState(blockPos.getX(), blockPos.getY(), blockPos.getZ());
   }

   public BlockState debugBlockState(int x, int y, int z) {
      BlockState blockState = null;
      if (y == 60) {
         blockState = Blocks.BARRIER.defaultBlockState();
      } else if (y == 70) {
         blockState = DebugLevelSource.getBlockStateFor(x, z);
      }

      return blockState == null ? Blocks.AIR.defaultBlockState() : blockState;
   }
}
