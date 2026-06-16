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
import com.xeno.render.chunk.build.RenderRegion;

public class ArrayLightDataCache extends LightDataAccess {
   private static final int NEIGHBOR_BLOCK_RADIUS = 2;
   private static final int BLOCK_LENGTH = 20;
   private final int[] light = new int[8000];
   private int xOffset;
   private int yOffset;
   private int zOffset;
   private int baseOffset;
   private int lastX = Integer.MIN_VALUE;
   private int lastY = Integer.MIN_VALUE;
   private int lastZ = Integer.MIN_VALUE;
   private int lastWord;

   public ArrayLightDataCache() {
   }

   private void updateBaseOffset() {
      this.baseOffset = -this.zOffset * 400 - this.yOffset * 20 - this.xOffset;
   }

   private void resetCache() {
      this.lastX = Integer.MIN_VALUE;
      this.lastY = Integer.MIN_VALUE;
      this.lastZ = Integer.MIN_VALUE;
      this.lastWord = 0;
   }

   public void reset(BlockAndTintGetter blockAndTintGetter, int x, int y, int z) {
      this.region = blockAndTintGetter;
      this.rrRegion = blockAndTintGetter instanceof RenderRegion rr ? rr : null;
      this.xOffset = x - 2;
      this.yOffset = y - 2;
      this.zOffset = z - 2;
      this.updateBaseOffset();
      Arrays.fill(this.light, 0);
      this.resetCache();
   }

   public void reset(BlockAndTintGetter blockAndTintGetter, BlockPos origin) {
      this.region = blockAndTintGetter;
      this.rrRegion = blockAndTintGetter instanceof RenderRegion rr ? rr : null;
      this.xOffset = origin.getX() - 2;
      this.yOffset = origin.getY() - 2;
      this.zOffset = origin.getZ() - 2;
      this.updateBaseOffset();
      Arrays.fill(this.light, 0);
      this.resetCache();
   }

   public void reset(SectionPos origin) {
      this.xOffset = origin.minBlockX() - 2;
      this.yOffset = origin.minBlockY() - 2;
      this.zOffset = origin.minBlockZ() - 2;
      this.updateBaseOffset();
      Arrays.fill(this.light, 0);
      this.resetCache();
   }

   public void reset(BlockPos origin) {
      this.xOffset = origin.getX() - 2;
      this.yOffset = origin.getY() - 2;
      this.zOffset = origin.getZ() - 2;
      this.updateBaseOffset();
      Arrays.fill(this.light, 0);
      this.resetCache();
   }

   @Override
   public int get(int x, int y, int z) {
      if (this.lastX == x && this.lastY == y && this.lastZ == z) {
         return this.lastWord;
      }
      int l = z * 400 + y * 20 + x + this.baseOffset;
      int word = this.light[l];
      if (word == 0) {
         word = this.compute(x, y, z);
         this.light[l] = word;
      }
      this.lastX = x;
      this.lastY = y;
      this.lastZ = z;
      this.lastWord = word;
      return word;
   }
}
