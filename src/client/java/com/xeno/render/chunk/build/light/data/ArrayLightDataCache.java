package com.xeno.render.chunk.build.light.data;

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
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public class ArrayLightDataCache extends LightDataAccess {
   private static final int NEIGHBOR_BLOCK_RADIUS = 2;
   private static final int BLOCK_LENGTH = 20;
   private final int[] light = new int[8000];
   private int xOffset;
   private int yOffset;
   private int zOffset;

   public ArrayLightDataCache() {
   }

   public void reset(BlockAndTintGetter blockAndTintGetter, int x, int y, int z) {
      this.region = blockAndTintGetter;
      this.xOffset = x - 2;
      this.yOffset = y - 2;
      this.zOffset = z - 2;
      Arrays.fill(this.light, 0);
   }

   public void reset(BlockAndTintGetter blockAndTintGetter, BlockPos origin) {
      this.region = blockAndTintGetter;
      this.xOffset = origin.getX() - 2;
      this.yOffset = origin.getY() - 2;
      this.zOffset = origin.getZ() - 2;
      Arrays.fill(this.light, 0);
   }

   public void reset(SectionPos origin) {
      this.xOffset = origin.minBlockX() - 2;
      this.yOffset = origin.minBlockY() - 2;
      this.zOffset = origin.minBlockZ() - 2;
      Arrays.fill(this.light, 0);
   }

   public void reset(BlockPos origin) {
      this.xOffset = origin.getX() - 2;
      this.yOffset = origin.getY() - 2;
      this.zOffset = origin.getZ() - 2;
      Arrays.fill(this.light, 0);
   }

   private int index(int x, int y, int z) {
      int x2 = x - this.xOffset;
      int y2 = y - this.yOffset;
      int z2 = z - this.zOffset;
      return z2 * 20 * 20 + y2 * 20 + x2;
   }

   @Override
   public int get(int x, int y, int z) {
      int l = this.index(x, y, z);
      int word = this.light[l];
      return word != 0 ? word : (this.light[l] = this.compute(x, y, z));
   }
}
