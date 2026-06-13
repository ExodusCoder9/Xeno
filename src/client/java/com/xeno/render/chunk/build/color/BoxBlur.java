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


public abstract class BoxBlur {
   public BoxBlur() {
   }

   public static void blur(int[] buffer, int[] temp, int width, int filterRadius) {
      horizontalBlur(buffer, temp, 0, width, filterRadius);
      horizontalBlur(temp, buffer, filterRadius, width, filterRadius);
   }

   public static void horizontalBlur(int[] src, int[] dst, int y0, int width, int filterRadius) {
      int div = filterRadius * 2 + 1;
      int x0 = filterRadius;
      int totalWidth = filterRadius * 2 + width;

      for (int y = y0; y < totalWidth; y++) {
         int r = 0;
         int g = 0;
         int b = 0;

         for (int x = 0; x < x0 + 1 + filterRadius; x++) {
            int color = src[getIdx(x, y, totalWidth)];
            r += unpackR(color);
            g += unpackG(color);
            b += unpackB(color);
         }

         dst[getIdx(y, x0, totalWidth)] = packColor(r, g, b, div);

         for (int x = x0 + 1; x < x0 + width; x++) {
            int color = src[getIdx(x - filterRadius - 1, y, totalWidth)];
            int var16 = r - unpackR(color);
            int var17 = g - unpackG(color);
            int var18 = b - unpackB(color);
            color = src[getIdx(x + filterRadius, y, totalWidth)];
            r = var16 + unpackR(color);
            g = var17 + unpackG(color);
            b = var18 + unpackB(color);
            dst[getIdx(y, x, totalWidth)] = packColor(r, g, b, div);
         }
      }
   }

   public static int getIdx(int x, int z, int width) {
      return x + z * width;
   }

   public static int unpackR(int color) {
      return color >> 16 & 0xFF;
   }

   public static int unpackG(int color) {
      return color >> 8 & 0xFF;
   }

   public static int unpackB(int color) {
      return color & 0xFF;
   }

   public static int packColor(int r, int g, int b, int div) {
      return 0xFF000000 | (r / div & 0xFF) << 16 | (g / div & 0xFF) << 8 | b / div & 0xFF;
   }
}
