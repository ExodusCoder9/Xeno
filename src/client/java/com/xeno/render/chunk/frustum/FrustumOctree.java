package com.xeno.render.chunk.frustum;

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
import com.xeno.render.chunk.ChunkArea;
import com.xeno.render.chunk.ChunkAreaManager;
import org.joml.Vector3i;

public class FrustumOctree {
   static final int LEVELS = 2;

   public FrustumOctree() {
   }

   public static void updateFrustumVisibility(VFrustum frustum, ChunkArea[] chunkAreas) {
      int width = 1 << ChunkAreaManager.AREA_SH_XZ + 4;

      for (ChunkArea chunkArea : chunkAreas) {
         Vector3i position = chunkArea.getPosition();
         int minX2 = position.x;
         int minY2 = position.y;
         int minZ2 = position.z;
         int frustumResult = frustum.cubeInFrustum(minX2, minY2, minZ2, minX2 + width, minY2 + width, minZ2 + width);
         byte[] buffer = chunkArea.getFrustumBuffer();
         if (frustumResult != -1) {
            Arrays.fill(buffer, (byte)frustumResult);
         } else {
            innerCube(frustum, buffer, 2, minX2, minY2, minZ2, width, 0);
         }
      }
   }

   static void innerCube(VFrustum frustum, byte[] buffer, int level, float xMin, float yMin, float zMin, int prevWidth, int beginIdx) {
      if (level == 1) {
         lastInnerCube(frustum, buffer, xMin, yMin, zMin, prevWidth, beginIdx);
      } else {
         int width = prevWidth >> 1;
         int lvlShift = (level - 1) * 3;

         for (int x = 0; x < 2; x++) {
            float xMin2 = xMin + x * width;
            float xMax2 = xMin2 + width;

            for (int y = 0; y < 2; y++) {
               float yMin2 = yMin + y * width;
               float yMax2 = yMin2 + width;

               for (int z = 0; z < 2; z++) {
                  float zMin2 = zMin + z * width;
                  float zMax2 = zMin2 + width;
                  int frustumResult = frustum.cubeInFrustum(xMin2, yMin2, zMin2, xMax2, yMax2, zMax2);
                  int idx = beginIdx + getOffset(lvlShift, x, y, z);
                  int endIdx = idx + (1 << lvlShift);
                  if (frustumResult != -1) {
                     fillResultBuffer(buffer, idx, endIdx, (byte)frustumResult);
                  } else {
                     innerCube(frustum, buffer, level - 1, xMin2, yMin2, zMin2, width, idx);
                  }
               }
            }
         }
      }
   }

   static void lastInnerCube(VFrustum frustum, byte[] buffer, float xMin, float yMin, float zMin, int prevWidth, int beginIdx) {
      int width = prevWidth >> 1;

      for (int x = 0; x < 2; x++) {
         float xMin2 = xMin + x * width;
         float xMax2 = xMin2 + width;

         for (int y = 0; y < 2; y++) {
            float yMin2 = yMin + y * width;
            float yMax2 = yMin2 + width;

            for (int z = 0; z < 2; z++) {
               float zMin2 = zMin + z * width;
               float zMax2 = zMin2 + width;
               int frustumResult = frustum.cubeInFrustum(xMin2, yMin2, zMin2, xMax2, yMax2, zMax2);
               int idx = beginIdx + (x << 2) + (y << 1) + z;
               buffer[idx] = (byte)frustumResult;
            }
         }
      }
   }

   static int getOffset(int baseShift, int x, int y, int z) {
      return (x << 2 + baseShift) + (y << 1 + baseShift) + (z << baseShift);
   }

   static void fillResultBuffer(byte[] buffer, int beginIdx, int endIdx, byte result) {
      for (int i = beginIdx; i < endIdx; i++) {
         buffer[i] = result;
      }
   }
}
