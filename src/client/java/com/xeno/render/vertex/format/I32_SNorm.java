package com.xeno.render.vertex.format;

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


public abstract class I32_SNorm {
   private static final float NORM_INV = 0.007874016F;

   public I32_SNorm() {
   }

   public static int packNormal(float x, float y, float z) {
      x *= 127.0F;
      y *= 127.0F;
      z *= 127.0F;
      return (int)x & 0xFF | ((int)y & 0xFF) << 8 | ((int)z & 0xFF) << 16;
   }

   public static int packNormal(int x, int y, int z) {
      return x & 0xFF | (y & 0xFF) << 8 | (z & 0xFF) << 16;
   }

   public static float unpackX(int i) {
      return (byte)(i & 0xFF) * 0.007874016F;
   }

   public static float unpackY(int i) {
      return (byte)(i >> 8 & 0xFF) * 0.007874016F;
   }

   public static float unpackZ(int i) {
      return (byte)(i >> 16 & 0xFF) * 0.007874016F;
   }
}
