package com.xeno.render.model.quad;

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


public abstract class QuadUtils {
   public static final byte DEFAULT_START_IDX = 0;
   public static final byte FLIPPED_START_IDX = 3;

   public QuadUtils() {
   }

   public static int getIterationStartIdx(float[] aos, int[] lms) {
      float ao00_11 = aos[0] + aos[2];
      float ao10_01 = aos[1] + aos[3];
      if (ao00_11 > ao10_01) {
         return 0;
      }

      if (ao00_11 < ao10_01) {
         return 3;
      }

      float lm00_11 = lms[0] + lms[2];
      float lm10_01 = lms[1] + lms[3];
      return lm00_11 >= lm10_01 ? 3 : 0;
   }

   public static int getIterationStartIdx(float[] aos) {
      float ao00_11 = aos[0] + aos[2];
      float ao10_01 = aos[1] + aos[3];
      return ao00_11 >= ao10_01 ? 0 : 3;
   }
}
