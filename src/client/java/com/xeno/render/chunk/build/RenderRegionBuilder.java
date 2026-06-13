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


import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import com.xeno.interfaces.biome.BiomeManagerExtended;
import com.xeno.render.chunk.build.biome.BiomeData;

public class RenderRegionBuilder {
   private static final DataLayer DEFAULT_SKY_LIGHT_DATA_LAYER = new DataLayer(15);
   private static final DataLayer DEFAULT_BLOCK_LIGHT_DATA_LAYER = new DataLayer(0);
   private static final int MAX_CACHE_ENTRIES = 256;
   private final Long2ReferenceLinkedOpenHashMap<LevelChunk> levelChunkCache = new Long2ReferenceLinkedOpenHashMap(256);

   public RenderRegionBuilder() {
   }

   public RenderRegion createRegion(Level level, int secX, int secY, int secZ) {
      LevelChunk levelChunk = this.getLevelChunk(level, secX, secZ);
      LevelChunkSection[] sections = levelChunk.getSections();
      LevelChunkSection section = sections[level.getSectionIndexFromSectionY(secY)];
      if (section != null && !section.hasOnlyAir()) {
         Map<BlockPos, BlockEntity> blockEntityMap = levelChunk.getBlockEntities();
         int minSecX = secX - 1;
         int minSecZ = secZ - 1;
         int minSecY = secY - 1;
         int maxSecX = secX + 1;
         int maxSecZ = secZ + 1;
         int maxSecY = secY + 1;
         PalettedContainer<BlockState>[] blockData = new PalettedContainer[27];
         DataLayer[][] lightData = new DataLayer[27][2];
         long biomeZoomSeed = BiomeManagerExtended.of(level.getBiomeManager()).getBiomeZoomSeed();
         BiomeData biomeData = new BiomeData(biomeZoomSeed, minSecX, minSecY, minSecZ);
         int minHeightSec = level.getMinY() >> 4;

         for (int x = minSecX; x <= maxSecX; x++) {
            for (int z = minSecZ; z <= maxSecZ; z++) {
               LevelChunk levelChunk1 = this.getLevelChunk(level, x, z);
               sections = levelChunk1.getSections();

               for (int y = minSecY; y <= maxSecY; y++) {
                  int sectionIdx = y - minHeightSec;
                  section = sectionIdx >= 0 && sectionIdx < sections.length ? sections[sectionIdx] : null;
                  int relX = x - minSecX;
                  int relY = y - minSecY;
                  int relZ = z - minSecZ;
                  int idx = (relY * 3 + relZ) * 3 + relX;
                  PalettedContainer<BlockState> values = section != null && !section.hasOnlyAir() ? section.getStates().copy() : null;
                  blockData[idx] = values;
                  SectionPos pos = SectionPos.of(x, y, z);
                  DataLayer[] dataLayers = this.getSectionDataLayers(level, pos);
                  lightData[idx] = dataLayers;
                  biomeData.getBiomeData(level, section, relX, relY, relZ);
               }
            }
         }

         return new RenderRegion(level, secX, secY, secZ, blockData, lightData, biomeData, blockEntityMap);
      } else {
         return null;
      }
   }

   private DataLayer[] getSectionDataLayers(Level level, SectionPos pos) {
      DataLayer[] dataLayers = new DataLayer[2];
      DataLayer blockDataLayer = level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(pos);
      if (blockDataLayer == null) {
         blockDataLayer = DEFAULT_BLOCK_LIGHT_DATA_LAYER;
      }

      dataLayers[LightLayer.BLOCK.ordinal()] = blockDataLayer;
      DataLayer skyDataLayer;
      if (level.dimensionType().hasSkyLight()) {
         skyDataLayer = level.getLightEngine().getLayerListener(LightLayer.SKY).getDataLayerData(pos);
         if (skyDataLayer == null) {
            skyDataLayer = DEFAULT_SKY_LIGHT_DATA_LAYER;
         }
      } else {
         skyDataLayer = null;
      }

      dataLayers[LightLayer.SKY.ordinal()] = skyDataLayer;
      return dataLayers;
   }

   private LevelChunk getLevelChunk(Level level, int x, int z) {
      long l = ChunkPos.pack(x, z);
      LevelChunk chunk = (LevelChunk)this.levelChunkCache.getAndMoveToFirst(l);
      if (chunk == null) {
         chunk = level.getChunk(x, z);

         while (this.levelChunkCache.size() >= 256) {
            this.levelChunkCache.removeLast();
         }

         this.levelChunkCache.putAndMoveToFirst(l, chunk);
      }

      return chunk;
   }

   public void remove(int x, int z) {
      this.levelChunkCache.remove(ChunkPos.pack(x, z));
   }

   public void clear() {
      this.levelChunkCache.clear();
   }
}
