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
package com.xeno.render.chunk;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import com.xeno.render.chunk.buffer.DrawBuffers;
import com.xeno.render.chunk.frustum.VFrustum;
import com.xeno.render.chunk.util.StaticQueue;
import org.joml.Vector3i;

public class ChunkArea {
   public final int index;
   final DrawBuffers drawBuffers;
   final Vector3i position;
   final byte[] frustumBuffer = new byte[64];
   int sectionsContained = 0;
   public final StaticQueue<RenderSection> sectionQueue = new StaticQueue<>(512);

   public ChunkArea(int i, Vector3i origin, int minHeight) {
      this.index = i;
      this.position = origin;
      this.drawBuffers = new DrawBuffers(i, origin, minHeight);
   }

   public void updateFrustum(VFrustum frustum) {
      int frustumResult = frustum.cubeInFrustum(
         this.position.x(), this.position.y(), this.position.z(), this.position.x() + 128, this.position.y() + 128, this.position.z() + 128
      );
      if (frustumResult == -1) {
         int width = 128;
         int l = width >> 1;

         for (int x1 = 0; x1 < 2; x1++) {
            float xMin = this.position.x() + x1 * l;
            float xMax = xMin + l;

            for (int y1 = 0; y1 < 2; y1++) {
               float yMin = this.position.y() + y1 * l;
               float yMax = yMin + l;

               for (int z1 = 0; z1 < 2; z1++) {
                  float zMin = this.position.z() + z1 * l;
                  float zMax = zMin + l;
                  frustumResult = frustum.cubeInFrustum(xMin, yMin, zMin, xMax, yMax, zMax);
                  int beginIdx = (x1 << 5) + (y1 << 4) + (z1 << 3);
                  if (frustumResult != -1) {
                     int end = beginIdx + 8;

                     for (int i = beginIdx; i < end; i++) {
                        this.frustumBuffer[i] = (byte)frustumResult;
                     }
                  } else {
                     int l2 = width >> 2;

                     for (int x2 = 0; x2 < 2; x2++) {
                        float xMin2 = xMin + x2 * l2;
                        float xMax2 = xMin2 + l2;

                        for (int y2 = 0; y2 < 2; y2++) {
                           float yMin2 = yMin + y2 * l2;
                           float yMax2 = yMin2 + l2;

                           for (int z2 = 0; z2 < 2; z2++) {
                              float zMin2 = zMin + z2 * l2;
                              float zMax2 = zMin2 + l2;
                              frustumResult = frustum.cubeInFrustum(xMin2, yMin2, zMin2, xMax2, yMax2, zMax2);
                              int idx = beginIdx + (x2 << 2) + (y2 << 1) + z2;
                              this.frustumBuffer[idx] = (byte)frustumResult;
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         Arrays.fill(this.frustumBuffer, (byte)frustumResult);
      }
   }

   public byte getFrustumIndex(BlockPos pos) {
      return this.getFrustumIndex(pos.getX(), pos.getY(), pos.getZ());
   }

   public byte getFrustumIndex(int x, int y, int z) {
      int dx = x - this.position.x;
      int dy = y - this.position.y;
      int dz = z - this.position.z;
      int i = (dx >> 1 & 32) + (dy >> 2 & 16) + (dz >> 3 & 8);
      int xSub = dx >> 3 & 4;
      int ySub = dy >> 4 & 2;
      int zSub = dz >> 5 & 1;
      return (byte)(i + xSub + ySub + zSub);
   }

   public byte inFrustum(byte i) {
      return this.frustumBuffer[i];
   }

   public byte[] getFrustumBuffer() {
      return this.frustumBuffer;
   }

   public DrawBuffers getDrawBuffers() {
      return this.drawBuffers;
   }

   public void resetQueue() {
      this.sectionQueue.clear();
   }

   public void setPosition(int x, int y, int z) {
      this.position.set(x, y, z);
   }

   public Vector3i getPosition() {
      return this.position;
   }

   public void addSection() {
      this.sectionsContained++;
   }

   public void removeSection() {
      this.sectionsContained--;
      if (this.sectionsContained == 0) {
         this.drawBuffers.releaseBuffers();
      }
   }

   public void releaseBuffers() {
      this.drawBuffers.releaseBuffers();
   }

   public void free() {
      this.drawBuffers.free();
   }
}
