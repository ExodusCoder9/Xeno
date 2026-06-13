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
package com.xeno.render.util;

import com.xeno.render.vertex.format.I32_SNorm;
import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MathUtil {
   public static float clamp(float min, float max, float x) {
      return Math.min(Math.max(x, min), max);
   }

   public static int clamp(int min, int max, int x) {
      return Math.min(Math.max(x, min), max);
   }

   public static float saturate(float x) {
      return clamp(0.0F, 1.0F, x);
   }

   public static float lerp(float v0, float v1, float t) {
      return v0 + t * (v1 - v0);
   }

   public static float transformX(Matrix4f mat, float x, float y, float z) {
      return Math.fma(mat.m00(), x, Math.fma(mat.m10(), y, Math.fma(mat.m20(), z, mat.m30())));
   }

   public static float transformY(Matrix4f mat, float x, float y, float z) {
      return Math.fma(mat.m01(), x, Math.fma(mat.m11(), y, Math.fma(mat.m21(), z, mat.m31())));
   }

   public static float transformZ(Matrix4f mat, float x, float y, float z) {
      return Math.fma(mat.m02(), x, Math.fma(mat.m12(), y, Math.fma(mat.m22(), z, mat.m32())));
   }

   public static int packTransformedNorm(Matrix3f mat, boolean trustedNormals, float x, float y, float z) {
      float nx = transformNormX(mat, x, y, z);
      float ny = transformNormY(mat, x, y, z);
      float nz = transformNormZ(mat, x, y, z);
      if (!trustedNormals) {
         float scalar = Math.invsqrt(Math.fma(nx, nx, Math.fma(ny, ny, nz * nz)));
         nx *= scalar;
         ny *= scalar;
         nz *= scalar;
      }

      return I32_SNorm.packNormal(nx, ny, nz);
   }

   public static float transformNormX(Matrix3f mat, float x, float y, float z) {
      return Math.fma(mat.m00(), x, Math.fma(mat.m10(), y, mat.m20() * z));
   }

   public static float transformNormY(Matrix3f mat, float x, float y, float z) {
      return Math.fma(mat.m01(), x, Math.fma(mat.m11(), y, mat.m21() * z));
   }

   public static float transformNormZ(Matrix3f mat, float x, float y, float z) {
      return Math.fma(mat.m02(), x, Math.fma(mat.m12(), y, mat.m22() * z));
   }
}
