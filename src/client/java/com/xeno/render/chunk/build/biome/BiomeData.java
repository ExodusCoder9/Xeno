package com.xeno.render.chunk.build.biome;

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


import net.minecraft.core.registries.Registries;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3f;

public class BiomeData {
   private static final int ZOOM_LENGTH = 4;
   private static final int BIOMES_PER_SECTION = 64;
   private static final int SIZE = 1728;
   Biome[] biomes = new Biome[1728];
   private final long biomeZoomSeed;
   int secX;
   int secY;
   int secZ;
   Vector3f[] offsets = new Vector3f[1728];
   private int lastX = Integer.MIN_VALUE;
   private int lastY = Integer.MIN_VALUE;
   private int lastZ = Integer.MIN_VALUE;
   private Biome lastResult;

   public BiomeData(long biomeZoomSeed, int secX, int secY, int secZ) {
      this.biomeZoomSeed = biomeZoomSeed;
      this.secX = secX;
      this.secY = secY;
      this.secZ = secZ;
   }

    public void getBiomeData(Level level, LevelChunkSection chunkSection, int secX, int secY, int secZ, Biome defaultValue) {
       int baseIdx = getRelativeSectionIdx(secX, secY, secZ);
 
       for (int x = 0; x < 4; x++) {
          for (int y = 0; y < 4; y++) {
             for (int z = 0; z < 4; z++) {
                int relIdx = getRelativeIdx(x, y, z);
                int idx = baseIdx + relIdx;
                if (chunkSection != null) {
                   this.biomes[idx] = (Biome)chunkSection.getNoiseBiome(x, y, z).value();
                } else {
                   this.biomes[idx] = defaultValue;
                }
             }
          }
       }
    }

   public Biome getBiome(int blockX, int blockY, int blockZ) {
      if (this.lastX == blockX && this.lastY == blockY && this.lastZ == blockZ) {
         return this.lastResult;
      }

      int x = blockX - 2;
      int y = blockY - 2;
      int z = blockZ - 2;
      int zoomX = x >> 2;
      int zoomY = y >> 2;
      int zoomZ = z >> 2;
      float fracZoomX = (x & 3) * 0.25F;
      float fracZoomY = (y & 3) * 0.25F;
      float fracZoomZ = (z & 3) * 0.25F;

      int zoomX0 = zoomX;
      int zoomY0 = zoomY;
      int zoomZ0 = zoomZ;
      int zoomX1 = zoomX + 1;
      int zoomY1 = zoomY + 1;
      int zoomZ1 = zoomZ + 1;

      float fCellX0 = fracZoomX;
      float fCellY0 = fracZoomY;
      float fCellZ0 = fracZoomZ;
      float fCellX1 = fracZoomX - 1.0F;
      float fCellY1 = fracZoomY - 1.0F;
      float fCellZ1 = fracZoomZ - 1.0F;

      int rx0 = (zoomX0 & 3) << 4;
      int ry0 = (zoomY0 & 3) << 2;
      int rz0 = zoomZ0 & 3;
      int rx1 = (zoomX1 & 3) << 4;
      int ry1 = (zoomY1 & 3) << 2;
      int rz1 = zoomZ1 & 3;

      int sx0 = ((zoomX0 >> 2) - this.secX) * 9;
      int sy0 = ((zoomY0 >> 2) - this.secY) * 3;
      int sz0 = (zoomZ0 >> 2) - this.secZ;
      int sx1 = ((zoomX1 >> 2) - this.secX) * 9;
      int sy1 = ((zoomY1 >> 2) - this.secY) * 3;
      int sz1 = (zoomZ1 >> 2) - this.secZ;

      int closestCellIdx = 0;
      float closestDistance = Float.POSITIVE_INFINITY;

      // 0: (0, 0, 0)
      {
         int baseSectionIdx = (sx0 + sy0 + sz0) * 64;
         int cellIdx = baseSectionIdx + rx0 + ry0 + rz0;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX0, zoomY0, zoomZ0);
         float dx = fCellX0 + offset.x;
         float dy = fCellY0 + offset.y;
         float dz = fCellZ0 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }
      // 1: (0, 0, 1)
      {
         int baseSectionIdx = (sx0 + sy0 + sz1) * 64;
         int cellIdx = baseSectionIdx + rx0 + ry0 + rz1;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX0, zoomY0, zoomZ1);
         float dx = fCellX0 + offset.x;
         float dy = fCellY0 + offset.y;
         float dz = fCellZ1 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }
      // 2: (0, 1, 0)
      {
         int baseSectionIdx = (sx0 + sy1 + sz0) * 64;
         int cellIdx = baseSectionIdx + rx0 + ry1 + rz0;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX0, zoomY1, zoomZ0);
         float dx = fCellX0 + offset.x;
         float dy = fCellY1 + offset.y;
         float dz = fCellZ0 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }
      // 3: (0, 1, 1)
      {
         int baseSectionIdx = (sx0 + sy1 + sz1) * 64;
         int cellIdx = baseSectionIdx + rx0 + ry1 + rz1;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX0, zoomY1, zoomZ1);
         float dx = fCellX0 + offset.x;
         float dy = fCellY1 + offset.y;
         float dz = fCellZ1 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }
      // 4: (1, 0, 0)
      {
         int baseSectionIdx = (sx1 + sy0 + sz0) * 64;
         int cellIdx = baseSectionIdx + rx1 + ry0 + rz0;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX1, zoomY0, zoomZ0);
         float dx = fCellX1 + offset.x;
         float dy = fCellY0 + offset.y;
         float dz = fCellZ0 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }
      // 5: (1, 0, 1)
      {
         int baseSectionIdx = (sx1 + sy0 + sz1) * 64;
         int cellIdx = baseSectionIdx + rx1 + ry0 + rz1;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX1, zoomY0, zoomZ1);
         float dx = fCellX1 + offset.x;
         float dy = fCellY0 + offset.y;
         float dz = fCellZ1 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }
      // 6: (1, 1, 0)
      {
         int baseSectionIdx = (sx1 + sy1 + sz0) * 64;
         int cellIdx = baseSectionIdx + rx1 + ry1 + rz0;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX1, zoomY1, zoomZ0);
         float dx = fCellX1 + offset.x;
         float dy = fCellY1 + offset.y;
         float dz = fCellZ0 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }
      // 7: (1, 1, 1)
      {
         int baseSectionIdx = (sx1 + sy1 + sz1) * 64;
         int cellIdx = baseSectionIdx + rx1 + ry1 + rz1;
         Vector3f offset = this.getOffset(baseSectionIdx, zoomX1, zoomY1, zoomZ1);
         float dx = fCellX1 + offset.x;
         float dy = fCellY1 + offset.y;
         float dz = fCellZ1 + offset.z;
         float distance = dx * dx + dy * dy + dz * dz;
         if (closestDistance > distance) {
            closestCellIdx = cellIdx;
            closestDistance = distance;
         }
      }

      this.lastX = blockX;
      this.lastY = blockY;
      this.lastZ = blockZ;
      this.lastResult = this.biomes[closestCellIdx];
      return this.lastResult;
   }

   private int getSectionIdx(int secX, int secY, int secZ) {
      return getRelativeSectionIdx(secX - this.secX, secY - this.secY, secZ - this.secZ);
   }

   private Vector3f getOffset(int baseIndex, int cellX, int cellY, int cellZ) {
      int relCellX = cellX & 3;
      int relCellY = cellY & 3;
      int relCellZ = cellZ & 3;
      int idx = baseIndex + getRelativeIdx(relCellX, relCellY, relCellZ);
      if (this.offsets[idx] == null) {
         this.offsets[idx] = computeCellOffset(this.biomeZoomSeed, cellX, cellY, cellZ);
      }

      return this.offsets[idx];
   }

   private static Vector3f computeCellOffset(long l, int cellX, int cellY, int cellZ) {
      long seed = LinearCongruentialGenerator.next(l, cellX);
      seed = LinearCongruentialGenerator.next(seed, cellY);
      seed = LinearCongruentialGenerator.next(seed, cellZ);
      seed = LinearCongruentialGenerator.next(seed, cellX);
      seed = LinearCongruentialGenerator.next(seed, cellY);
      seed = LinearCongruentialGenerator.next(seed, cellZ);
      float xOffset = getFiddle(seed);
      seed = LinearCongruentialGenerator.next(seed, l);
      float yOffset = getFiddle(seed);
      seed = LinearCongruentialGenerator.next(seed, l);
      float zOffset = getFiddle(seed);
      return new Vector3f(xOffset, yOffset, zOffset);
   }

   private static float getFiddle(long l) {
      float d = Math.floorMod(l >> 24, 1024) * 9.765625E-4F;
      return (d - 0.5F) * 0.9F;
   }

   private static int getRelativeSectionIdx(int x, int y, int z) {
      return (x * 3 * 3 + y * 3 + z) * 64;
   }

   private static int getRelativeIdx(int x, int y, int z) {
      return x * 4 * 4 + y * 4 + z;
   }
}
