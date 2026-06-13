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

public class SubBlockAoFace extends AoFaceData {
   public SubBlockAoFace() {
   }

   @Override
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

      AoNeighborInfo aoInfo = AoNeighborInfo.get(direction);
      SimpleDirection[] faces = aoInfo.faces;
      int[] cbits = aoInfo.outCornerBits;
      int e0 = cache.get(x, y, z, faces[0]);
      int e0lm = LightDataAccess.getLightmap(e0);
      boolean e0em = LightDataAccess.unpackEM(e0);
      int e0co = LightDataAccess.unpackCO(e0);
      int e1 = cache.get(x, y, z, faces[1]);
      int e1lm = LightDataAccess.getLightmap(e1);
      boolean e1em = LightDataAccess.unpackEM(e1);
      int e1co = LightDataAccess.unpackCO(e1);
      int e2 = cache.get(x, y, z, faces[2]);
      int e2lm = LightDataAccess.getLightmap(e2);
      boolean e2em = LightDataAccess.unpackEM(e2);
      int e2co = LightDataAccess.unpackCO(e2);
      int e3 = cache.get(x, y, z, faces[3]);
      int e3lm = LightDataAccess.getLightmap(e3);
      boolean e3em = LightDataAccess.unpackEM(e3);
      int e3co = LightDataAccess.unpackCO(e3);
      boolean c0oc = offset && this.getCornerOcclusion(LightDataAccess.unpackCO(e), cbits, 4);
      boolean f0c0 = offset ? this.getCornerOcclusion(e0co, cbits, 0) : LightDataAccess.unpackFO(e0);
      boolean f1c0 = offset ? this.getCornerOcclusion(e1co, cbits, 1) : LightDataAccess.unpackFO(e1);
      int d0 = cache.get(x, y, z, faces[0], faces[1]);
      boolean d0co = this.getCornerOcclusion(LightDataAccess.unpackCO(d0), cbits, 2);
      float c0ao = c0oc ? 0.2F : 1.0F;
      if ((!f0c0 || !f1c0) && (!c0oc || !d0co)) {
         c0ao += f0c0 ? 0.2F : 1.0F;
         c0ao += f1c0 ? 0.2F : 1.0F;
         if (offset) {
            c0ao += d0co ? 0.2F : 1.0F;
         } else {
            c0ao += LightDataAccess.unpackFO(d0) ? 0.2F : 1.0F;
         }
      } else {
         c0ao = 1.6F;
      }

      int c0lm;
      boolean c0em;
      if (f0c0 && f1c0) {
         c0lm = e1lm;
         c0em = e1em;
      } else {
         c0lm = LightDataAccess.getLightmap(d0);
         c0em = LightDataAccess.unpackEM(d0);
      }

      boolean c1oc = offset && this.getCornerOcclusion(LightDataAccess.unpackCO(e), cbits, 0);
      boolean f1c1 = offset ? this.getCornerOcclusion(e1co, cbits, 3) : LightDataAccess.unpackFO(e1);
      boolean f2c1 = offset ? this.getCornerOcclusion(e2co, cbits, 4) : LightDataAccess.unpackFO(e2);
      int d1 = cache.get(x, y, z, faces[1], faces[2]);
      boolean d1co = this.getCornerOcclusion(LightDataAccess.unpackCO(d1), cbits, 5);
      float c1ao = c1oc ? 0.2F : 1.0F;
      if ((!f1c1 || !f2c1) && (!c1oc || !d1co)) {
         c1ao += f1c1 ? 0.2F : 1.0F;
         c1ao += f2c1 ? 0.2F : 1.0F;
         if (offset) {
            c1ao += d1co ? 0.2F : 1.0F;
         } else {
            c1ao += LightDataAccess.unpackFO(d1) ? 0.2F : 1.0F;
         }
      } else {
         c1ao = 1.6F;
      }

      int c1lm;
      boolean c1em;
      if (f1c1 && f2c1) {
         c1lm = e1lm;
         c1em = e1em;
      } else {
         c1lm = LightDataAccess.getLightmap(d1);
         c1em = LightDataAccess.unpackEM(d1);
      }

      boolean c2oc = offset && this.getCornerOcclusion(LightDataAccess.unpackCO(e), cbits, 2);
      boolean f2c2 = offset ? this.getCornerOcclusion(e2co, cbits, 6) : LightDataAccess.unpackFO(e2);
      boolean f3c2 = offset ? this.getCornerOcclusion(e3co, cbits, 7) : LightDataAccess.unpackFO(e3);
      int d2 = cache.get(x, y, z, faces[2], faces[3]);
      boolean d2co = this.getCornerOcclusion(LightDataAccess.unpackCO(d2), cbits, 8);
      float c2ao = c2oc ? 0.2F : 1.0F;
      if ((!f2c2 || !f3c2) && (!c2oc || !d2co)) {
         c2ao += f2c2 ? 0.2F : 1.0F;
         c2ao += f3c2 ? 0.2F : 1.0F;
         if (offset) {
            c2ao += d2co ? 0.2F : 1.0F;
         } else {
            c2ao += LightDataAccess.unpackFO(d2) ? 0.2F : 1.0F;
         }
      } else {
         c2ao = 1.6F;
      }

      int c2lm;
      boolean c2em;
      if (f2c2 && f3c2) {
         c2lm = e3lm;
         c2em = e3em;
      } else {
         c2lm = LightDataAccess.getLightmap(d2);
         c2em = LightDataAccess.unpackEM(d2);
      }

      boolean c3oc = offset && this.getCornerOcclusion(LightDataAccess.unpackCO(e), cbits, 1);
      boolean f3c3 = offset ? this.getCornerOcclusion(e3co, cbits, 9) : LightDataAccess.unpackFO(e3);
      boolean f0c3 = offset ? this.getCornerOcclusion(e0co, cbits, 10) : LightDataAccess.unpackFO(e0);
      int d3 = cache.get(x, y, z, faces[3], faces[0]);
      boolean d3co = this.getCornerOcclusion(LightDataAccess.unpackCO(d3), cbits, 11);
      float c3ao = c3oc ? 0.2F : 1.0F;
      if ((!f3c3 || !f0c3) && (!c3oc || !d3co)) {
         c3ao += f3c3 ? 0.2F : 1.0F;
         c3ao += f0c3 ? 0.2F : 1.0F;
         if (offset) {
            c3ao += d3co ? 0.2F : 1.0F;
         } else {
            c3ao += LightDataAccess.unpackFO(d3) ? 0.2F : 1.0F;
         }
      } else {
         c3ao = 1.6F;
      }

      int c3lm;
      boolean c3em;
      if (f3c3 && f0c3) {
         c3lm = e3lm;
         c3em = e3em;
      } else {
         c3lm = LightDataAccess.getLightmap(d3);
         c3em = LightDataAccess.unpackEM(d3);
      }

      float[] ao = this.ao;
      ao[0] = c0ao * 0.25F;
      ao[1] = c1ao * 0.25F;
      ao[2] = c2ao * 0.25F;
      ao[3] = c3ao * 0.25F;
      int[] cb = this.lm;
      cb[0] = calculateCornerBrightness(e0lm, e1lm, c0lm, olm, e0em, e1em, c0em, oem);
      cb[1] = calculateCornerBrightness(e1lm, e2lm, c1lm, olm, e1em, e2em, c1em, oem);
      cb[2] = calculateCornerBrightness(e2lm, e3lm, c2lm, olm, e2em, e3em, c2em, oem);
      cb[3] = calculateCornerBrightness(e3lm, e0lm, c3lm, olm, e3em, e0em, c3em, oem);
      this.flags |= 1;
   }

   public void calculateSelfOcclusion(LightDataAccess cache, BlockPos pos, SimpleDirection direction) {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      int e = cache.get(x, y, z);
      AoNeighborInfo aoInfo = AoNeighborInfo.get(direction);
      int[] cbits = aoInfo.inCornerBits;
      int co = LightDataAccess.unpackCO(e);
      boolean c0oc = this.getCornerOcclusion(co, cbits, 6);
      boolean c1oc = this.getCornerOcclusion(co, cbits, 4);
      boolean c2oc = this.getCornerOcclusion(co, cbits, 5);
      boolean c3oc = this.getCornerOcclusion(co, cbits, 7);
      float c0ao = c0oc ? 0.25F : 1.0F;
      float c1ao = c1oc ? 0.25F : 1.0F;
      float c2ao = c2oc ? 0.25F : 1.0F;
      float c3ao = c3oc ? 0.25F : 1.0F;
      float[] ao = this.ao;
      ao[0] = c0ao;
      ao[1] = c1ao;
      ao[2] = c2ao;
      ao[3] = c3ao;
   }

   public void calculatePartialAlignedFace(LightDataAccess cache, BlockPos pos, SimpleDirection direction) {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      int e = cache.get(x, y, z);
      int x2 = x + direction.getStepX();
      int y2 = y + direction.getStepY();
      int z2 = z + direction.getStepZ();
      int eb = cache.get(x2, y2, z2);
      AoNeighborInfo aoInfo = AoNeighborInfo.get(direction);
      SimpleDirection[] faces = aoInfo.faces;
      int[] cbits = aoInfo.inCornerBits;
      int co = LightDataAccess.unpackCO(e);
      int cob = LightDataAccess.unpackCO(eb);
      boolean c0oc = this.getCornerOcclusion(cob, cbits, 2) || this.getCornerOcclusion(co, cbits, 6);
      boolean c1oc = this.getCornerOcclusion(cob, cbits, 0) || this.getCornerOcclusion(co, cbits, 4);
      boolean c2oc = this.getCornerOcclusion(cob, cbits, 1) || this.getCornerOcclusion(co, cbits, 5);
      boolean c3oc = this.getCornerOcclusion(cob, cbits, 3) || this.getCornerOcclusion(co, cbits, 7);
      float c0ao = c0oc ? 0.0F : 3.0F;
      float c1ao = c1oc ? 0.0F : 3.0F;
      float c2ao = c2oc ? 0.0F : 3.0F;
      float c3ao = c3oc ? 0.0F : 3.0F;
      cbits = aoInfo.outCornerBits;
      int e1 = cache.get(x2, y2, z2, faces[1]);
      int e1co = LightDataAccess.unpackCO(e1);
      c0ao += this.getCornerOcclusion(e1co, cbits, 1) ? 0.0F : 1.0F;
      c1ao += this.getCornerOcclusion(e1co, cbits, 3) ? 0.0F : 1.0F;
      int e3 = cache.get(x2, y2, z2, faces[3]);
      int e3co = LightDataAccess.unpackCO(e3);
      c2ao += this.getCornerOcclusion(e3co, cbits, 0) ? 0.0F : 1.0F;
      c3ao += this.getCornerOcclusion(e3co, cbits, 4) ? 0.0F : 1.0F;
      float[] ao = this.ao;
      ao[0] = c0ao * 0.25F;
      ao[1] = c1ao * 0.25F;
      ao[2] = c2ao * 0.25F;
      ao[3] = c3ao * 0.25F;
   }
}
