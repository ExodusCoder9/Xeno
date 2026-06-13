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


import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class VFrustum {
   private Vector4f viewVector = new Vector4f();
   private double camX;
   private double camY;
   private double camZ;
   private final FrustumIntersection frustum = new FrustumIntersection();
   private final Matrix4f matrix = new Matrix4f();

   public VFrustum() {
   }

   public VFrustum offsetToFullyIncludeCameraCube(int offset) {
      double d0 = Math.floor(this.camX / offset) * offset;
      double d1 = Math.floor(this.camY / offset) * offset;
      double d2 = Math.floor(this.camZ / offset) * offset;
      double d3 = Math.ceil(this.camX / offset) * offset;
      double d4 = Math.ceil(this.camY / offset) * offset;

      for (double d5 = Math.ceil(this.camZ / offset) * offset;
         this.intersectAab(
               (float)(d0 - this.camX),
               (float)(d1 - this.camY),
               (float)(d2 - this.camZ),
               (float)(d3 - this.camX),
               (float)(d4 - this.camY),
               (float)(d5 - this.camZ)
            )
            >= 0;
         this.camY = this.camY - this.viewVector.y() * 4.0F
      ) {
         this.camZ = this.camZ - this.viewVector.z() * 4.0F;
         this.camX = this.camX - this.viewVector.x() * 4.0F;
      }

      return this;
   }

   public void setCamOffset(double camX, double camY, double camZ) {
      this.camX = camX;
      this.camY = camY;
      this.camZ = camZ;
   }

   public void calculateFrustum(Matrix4f modelViewMatrix, Matrix4f projMatrix) {
      projMatrix.mul(modelViewMatrix, this.matrix);
      this.frustum.set(this.matrix, false);
      this.viewVector = this.matrix.transformTranspose(new Vector4f(0.0F, 0.0F, 1.0F, 0.0F));
   }

   public int cubeInFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {
      float f = (float)(x1 - this.camX);
      float f1 = (float)(y1 - this.camY);
      float f2 = (float)(z1 - this.camZ);
      float f3 = (float)(x2 - this.camX);
      float f4 = (float)(y2 - this.camY);
      float f5 = (float)(z2 - this.camZ);
      return this.intersectAab(f, f1, f2, f3, f4, f5);
   }

   public boolean testFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {
      float f = (float)(x1 - this.camX);
      float f1 = (float)(y1 - this.camY);
      float f2 = (float)(z1 - this.camZ);
      float f3 = (float)(x2 - this.camX);
      float f4 = (float)(y2 - this.camY);
      float f5 = (float)(z2 - this.camZ);
      return this.frustum.testAab(f, f1, f2, f3, f4, f5);
   }

   private int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public boolean isVisible(AABB aABB) {
      return this.cubeInFrustum(aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ);
   }

   private boolean cubeInFrustum(double d, double e, double f, double g, double h, double i) {
      float j = (float)(d - this.camX);
      float k = (float)(e - this.camY);
      float l = (float)(f - this.camZ);
      float m = (float)(g - this.camX);
      float n = (float)(h - this.camY);
      float o = (float)(i - this.camZ);
      return this.frustum.testAab(j, k, l, m, n, o);
   }
}
