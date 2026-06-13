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


import net.minecraft.core.BlockPos;
import com.xeno.render.chunk.build.light.data.LightDataAccess;
import com.xeno.render.chunk.util.SimpleDirection;

class AoFaceData {
   public final int[] lm = new int[4];
   public final float[] ao = new float[4];
   public final float[] bl = new float[4];
   public final float[] sl = new float[4];
   protected int flags;

   AoFaceData() {
   }

   public void initLightData(LightDataAccess cache, BlockPos pos, SimpleDirection direction, boolean offset) {
      int oX = pos.getX();
      int oY = pos.getY();
      int oZ = pos.getZ();
      int x;
      int y;
      int z;
      if (offset) {
         x = oX + direction.getStepX();
         y = oY + direction.getStepY();
         z = oZ + direction.getStepZ();
      } else {
         x = oX;
         y = oY;
         z = oZ;
      }

      int e = cache.get(x, y, z);
      int olm;
      boolean oem;
      if (offset && LightDataAccess.unpackFO(e)) {
         int originWord = cache.get(oX, oY, oZ);
         olm = LightDataAccess.getLightmap(originWord);
         oem = LightDataAccess.unpackEM(originWord);
      } else {
         olm = LightDataAccess.getLightmap(e);
         oem = LightDataAccess.unpackEM(e);
      }

      float oao = LightDataAccess.unpackAO(e);
      SimpleDirection[] faces = AoNeighborInfo.get(direction).faces;
      int e0 = cache.get(x, y, z, faces[0]);
      int e0lm = LightDataAccess.getLightmap(e0);
      float e0ao = LightDataAccess.unpackAO(e0);
      boolean e0op = LightDataAccess.unpackOP(e0);
      boolean e0em = LightDataAccess.unpackEM(e0);
      int e1 = cache.get(x, y, z, faces[1]);
      int e1lm = LightDataAccess.getLightmap(e1);
      float e1ao = LightDataAccess.unpackAO(e1);
      boolean e1op = LightDataAccess.unpackOP(e1);
      boolean e1em = LightDataAccess.unpackEM(e1);
      int e2 = cache.get(x, y, z, faces[2]);
      int e2lm = LightDataAccess.getLightmap(e2);
      float e2ao = LightDataAccess.unpackAO(e2);
      boolean e2op = LightDataAccess.unpackOP(e2);
      boolean e2em = LightDataAccess.unpackEM(e2);
      int e3 = cache.get(x, y, z, faces[3]);
      int e3lm = LightDataAccess.getLightmap(e3);
      float e3ao = LightDataAccess.unpackAO(e3);
      boolean e3op = LightDataAccess.unpackOP(e3);
      boolean e3em = LightDataAccess.unpackEM(e3);
      int c0lm;
      float c0ao;
      boolean c0em;
      if (e0op && e1op) {
         c0lm = e1lm;
         c0ao = e1ao;
         c0em = e1em;
      } else {
         int d0 = cache.get(x, y, z, faces[0], faces[1]);
         c0lm = LightDataAccess.getLightmap(d0);
         c0ao = LightDataAccess.unpackAO(d0);
         c0em = LightDataAccess.unpackEM(d0);
      }

      float c1ao;
      boolean c1em;
      int c1lm;
      if (e1op && e2op) {
         c1lm = e1lm;
         c1ao = e1ao;
         c1em = e1em;
      } else {
         int d1 = cache.get(x, y, z, faces[1], faces[2]);
         c1lm = LightDataAccess.getLightmap(d1);
         c1ao = LightDataAccess.unpackAO(d1);
         c1em = LightDataAccess.unpackEM(d1);
      }

      float c2ao;
      boolean c2em;
      int c2lm;
      if (e2op && e3op) {
         c2lm = e3lm;
         c2ao = e3ao;
         c2em = e3em;
      } else {
         int d2 = cache.get(x, y, z, faces[2], faces[3]);
         c2lm = LightDataAccess.getLightmap(d2);
         c2ao = LightDataAccess.unpackAO(d2);
         c2em = LightDataAccess.unpackEM(d2);
      }

      float c3ao;
      boolean c3em;
      int c3lm;
      if (e3op && e0op) {
         c3lm = e3lm;
         c3ao = e3ao;
         c3em = e3em;
      } else {
         int d3 = cache.get(x, y, z, faces[3], faces[0]);
         c3lm = LightDataAccess.getLightmap(d3);
         c3ao = LightDataAccess.unpackAO(d3);
         c3em = LightDataAccess.unpackEM(d3);
      }

      float[] ao = this.ao;
      ao[0] = (e0ao + e1ao + c0ao + oao) * 0.25F;
      ao[1] = (e1ao + e2ao + c1ao + oao) * 0.25F;
      ao[2] = (e2ao + e3ao + c2ao + oao) * 0.25F;
      ao[3] = (e3ao + e0ao + c3ao + oao) * 0.25F;
      int[] cb = this.lm;
      cb[0] = calculateCornerBrightness(e0lm, e1lm, c0lm, olm, e0em, e1em, c0em, oem);
      cb[1] = calculateCornerBrightness(e1lm, e2lm, c1lm, olm, e1em, e2em, c1em, oem);
      cb[2] = calculateCornerBrightness(e2lm, e3lm, c2lm, olm, e2em, e3em, c2em, oem);
      cb[3] = calculateCornerBrightness(e3lm, e0lm, c3lm, olm, e3em, e0em, c3em, oem);
      this.flags |= 1;
   }

   public void unpackLightData() {
      int[] lm = this.lm;
      float[] bl = this.bl;
      float[] sl = this.sl;
      bl[0] = unpackBlockLight(lm[0]);
      bl[1] = unpackBlockLight(lm[1]);
      bl[2] = unpackBlockLight(lm[2]);
      bl[3] = unpackBlockLight(lm[3]);
      sl[0] = unpackSkyLight(lm[0]);
      sl[1] = unpackSkyLight(lm[1]);
      sl[2] = unpackSkyLight(lm[2]);
      sl[3] = unpackSkyLight(lm[3]);
      this.flags |= 2;
   }

   public boolean getCornerOcclusion(int bits, int[] values, int i) {
      return (bits & 1 << values[i]) != 0;
   }

   public float getBlendedSkyLight(float[] w) {
      return weightedSum(this.sl, w);
   }

   public float getBlendedBlockLight(float[] w) {
      return weightedSum(this.bl, w);
   }

   public float getBlendedShade(float[] w) {
      return weightedSum(this.ao, w);
   }

   static float weightedSum(float[] v, float[] w) {
      float t0 = v[0] * w[0];
      float t1 = v[1] * w[1];
      float t2 = v[2] * w[2];
      float t3 = v[3] * w[3];
      return t0 + t1 + t2 + t3;
   }

   static float unpackSkyLight(int i) {
      return i >> 16 & 0xFF;
   }

   static float unpackBlockLight(int i) {
      return i & 0xFF;
   }

   static int calculateCornerBrightness(int a, int b, int c, int d, boolean aem, boolean bem, boolean cem, boolean dem) {
      if (a == 0 || b == 0 || c == 0 || d == 0) {
         int min = minNonZero(minNonZero(a, b), minNonZero(c, d));
         a = Math.max(a, min);
         b = Math.max(b, min);
         c = Math.max(c, min);
         d = Math.max(d, min);
      }

      if (aem) {
         a = 15728880;
      }

      if (bem) {
         b = 15728880;
      }

      if (cem) {
         c = 15728880;
      }

      if (dem) {
         d = 15728880;
      }

      return a + b + c + d >> 2 & 16711935;
   }

   static int minNonZero(int a, int b) {
      if (a == 0) {
         return b;
      } else {
         return b == 0 ? a : Math.min(a, b);
      }
   }

   public boolean hasLightData() {
      return (this.flags & 1) != 0;
   }

   public boolean hasUnpackedLightData() {
      return (this.flags & 2) != 0;
   }

   public void reset() {
      this.flags = 0;
   }
}
