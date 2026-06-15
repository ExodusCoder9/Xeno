package com.xeno.render.chunk.build.light.smooth;

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


import com.xeno.render.chunk.util.SimpleDirection;

enum AoNeighborInfo {
   DOWN(
      new SimpleDirection[]{SimpleDirection.SOUTH, SimpleDirection.WEST, SimpleDirection.NORTH, SimpleDirection.EAST}, new int[]{4, 5, 6, 7, 0, 1, 2, 3}, 0.5F
   ) {
      @Override
      public void calculateCornerWeights(float x, float y, float z, float[] out) {
         float u = 1.0F - x;
         float v = z;
         calculateCornerWeights(u, v, out);
      }

      @Override
      public float getU(float x, float y, float z) {
         return 1.0F - x;
      }

      @Override
      public float getV(float x, float y, float z) {
         return z;
      }

      @Override
      public float getDepth(float x, float y, float z) {
         return y;
      }
   },
   UP(new SimpleDirection[]{SimpleDirection.NORTH, SimpleDirection.WEST, SimpleDirection.SOUTH, SimpleDirection.EAST}, new int[]{2, 3, 0, 1, 6, 7, 4, 5}, 1.0F) {
      @Override
      public void calculateCornerWeights(float x, float y, float z, float[] out) {
         float u = 1.0F - x;
         float v = 1.0F - z;
         calculateCornerWeights(u, v, out);
      }

      @Override
      public float getU(float x, float y, float z) {
         return 1.0F - x;
      }

      @Override
      public float getV(float x, float y, float z) {
         return 1.0F - z;
      }

      @Override
      public float getDepth(float x, float y, float z) {
         return 1.0F - y;
      }
   },
   NORTH(new SimpleDirection[]{SimpleDirection.UP, SimpleDirection.EAST, SimpleDirection.DOWN, SimpleDirection.WEST}, new int[]{3, 2, 7, 6, 1, 0, 5, 4}, 0.8F) {
      @Override
      public void calculateCornerWeights(float x, float y, float z, float[] out) {
         float u = x;
         float v = y;
         calculateCornerWeights(u, v, out);
      }

      @Override
      public float getU(float x, float y, float z) {
         return x;
      }

      @Override
      public float getV(float x, float y, float z) {
         return y;
      }

      @Override
      public float getDepth(float x, float y, float z) {
         return z;
      }
   },
   SOUTH(new SimpleDirection[]{SimpleDirection.UP, SimpleDirection.WEST, SimpleDirection.DOWN, SimpleDirection.EAST}, new int[]{0, 1, 4, 5, 2, 3, 6, 7}, 0.8F) {
      @Override
      public void calculateCornerWeights(float x, float y, float z, float[] out) {
         float u = 1.0F - x;
         float v = y;
         calculateCornerWeights(u, v, out);
      }

      @Override
      public float getU(float x, float y, float z) {
         return 1.0F - x;
      }

      @Override
      public float getV(float x, float y, float z) {
         return y;
      }

      @Override
      public float getDepth(float x, float y, float z) {
         return 1.0F - z;
      }
   },
   WEST(new SimpleDirection[]{SimpleDirection.UP, SimpleDirection.NORTH, SimpleDirection.DOWN, SimpleDirection.SOUTH}, new int[]{1, 3, 5, 7, 0, 2, 4, 6}, 0.6F) {
      @Override
      public void calculateCornerWeights(float x, float y, float z, float[] out) {
         float u = 1.0F - z;
         float v = y;
         calculateCornerWeights(u, v, out);
      }

      @Override
      public float getU(float x, float y, float z) {
         return 1.0F - z;
      }

      @Override
      public float getV(float x, float y, float z) {
         return y;
      }

      @Override
      public float getDepth(float x, float y, float z) {
         return x;
      }
   },
   EAST(new SimpleDirection[]{SimpleDirection.UP, SimpleDirection.SOUTH, SimpleDirection.DOWN, SimpleDirection.NORTH}, new int[]{2, 0, 6, 4, 3, 1, 7, 5}, 0.6F) {
      @Override
      public void calculateCornerWeights(float x, float y, float z, float[] out) {
         float u = z;
         float v = y;
         calculateCornerWeights(u, v, out);
      }

      @Override
      public float getU(float x, float y, float z) {
         return z;
      }

      @Override
      public float getV(float x, float y, float z) {
         return y;
      }

      @Override
      public float getDepth(float x, float y, float z) {
         return 1.0F - x;
      }
   };

   public final SimpleDirection[] faces;
   public final float strength;
   public final int[] inCornerBits = new int[8];
   public final int[] outCornerBits = new int[24];
   private static final AoNeighborInfo[] VALUES = values();

   AoNeighborInfo(SimpleDirection[] directions, int[] indices, float strength) {
      this.faces = directions;
      this.strength = strength;
      copyInCornerBits(this.inCornerBits, indices);
      getOutCornerBits(this.outCornerBits, indices);
   }

   public abstract void calculateCornerWeights(float var1, float var2, float var3, float[] var4);

   public abstract float getU(float var1, float var2, float var3);

   public abstract float getV(float var1, float var2, float var3);

   public void copyLightValues(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
      lm1[0] = lm0[0];
      lm1[1] = lm0[1];
      lm1[2] = lm0[2];
      lm1[3] = lm0[3];
      ao1[0] = ao0[0];
      ao1[1] = ao0[1];
      ao1[2] = ao0[2];
      ao1[3] = ao0[3];
   }

   public abstract float getDepth(float var1, float var2, float var3);

   public static AoNeighborInfo get(SimpleDirection direction) {
      return VALUES[direction.ordinal()];
   }

   public static void calculateCornerWeights(float u, float v, float[] out) {
      out[0] = u * v;
      out[1] = u * (1.0F - v);
      out[2] = (1.0F - u) * (1.0F - v);
      out[3] = (1.0F - u) * v;
   }

   private static void copyInCornerBits(int[] cornersBits, int[] idxs) {
      cornersBits[0] = idxs[0];
      cornersBits[1] = idxs[1];
      cornersBits[2] = idxs[2];
      cornersBits[3] = idxs[3];
      cornersBits[4] = idxs[4];
      cornersBits[5] = idxs[5];
      cornersBits[6] = idxs[6];
      cornersBits[7] = idxs[7];
   }

   private static void getOutCornerBits(int[] cornersBits, int[] idxs) {
      cornersBits[0] = idxs[0];
      cornersBits[1] = idxs[3];
      cornersBits[2] = idxs[1];
      cornersBits[3] = idxs[1];
      cornersBits[4] = idxs[2];
      cornersBits[5] = idxs[3];
      cornersBits[6] = idxs[3];
      cornersBits[7] = idxs[0];
      cornersBits[8] = idxs[2];
      cornersBits[9] = idxs[2];
      cornersBits[10] = idxs[1];
      cornersBits[11] = idxs[0];
      cornersBits[12] = idxs[4];
      cornersBits[13] = idxs[7];
      cornersBits[14] = idxs[5];
      cornersBits[15] = idxs[5];
      cornersBits[16] = idxs[6];
      cornersBits[17] = idxs[7];
      cornersBits[18] = idxs[7];
      cornersBits[19] = idxs[4];
      cornersBits[20] = idxs[6];
      cornersBits[21] = idxs[6];
      cornersBits[22] = idxs[5];
      cornersBits[23] = idxs[4];
   }
}
