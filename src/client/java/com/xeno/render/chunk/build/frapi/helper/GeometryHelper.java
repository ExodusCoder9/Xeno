package com.xeno.render.chunk.build.frapi.helper;

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


import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import org.joml.Vector3fc;

public abstract class GeometryHelper {
   public static final int CUBIC_FLAG = 1;
   public static final int AXIS_ALIGNED_FLAG = 2;
   public static final int LIGHT_FACE_FLAG = 4;
   public static final int FLAG_BIT_COUNT = 3;
   private static final float EPS_MIN = 1.0E-4F;
   private static final float EPS_MAX = 0.9999F;

   private GeometryHelper() {
   }

   public static int computeShapeFlags(QuadView quad) {
      Direction lightFace = quad.lightFace();
      int bits = 0;
      if (isQuadParallelToFace(lightFace, quad)) {
         bits |= 2;
         if (isParallelQuadOnFace(lightFace, quad)) {
            bits |= 4;
         }
      }

      if (isQuadCubic(lightFace, quad)) {
         bits |= 1;
      }

      return bits;
   }

   public static boolean isQuadParallelToFace(Direction face, QuadView quad) {
      int i = face.getAxis().ordinal();
      float val = quad.posByIndex(0, i);
      return Mth.equal(val, quad.posByIndex(1, i)) && Mth.equal(val, quad.posByIndex(2, i)) && Mth.equal(val, quad.posByIndex(3, i));
   }

   public static boolean isParallelQuadOnFace(Direction lightFace, QuadView quad) {
      float x = quad.posByIndex(0, lightFace.getAxis().ordinal());
      return lightFace.getAxisDirection() == AxisDirection.POSITIVE ? x >= 0.9999F : x <= 1.0E-4F;
   }

   public static boolean isQuadCubic(Direction lightFace, QuadView quad) {
      int a;
      int b;
      switch (lightFace) {
         case EAST:
         case WEST:
            a = 1;
            b = 2;
            break;
         case UP:
         case DOWN:
            a = 0;
            b = 2;
            break;
         case SOUTH:
         case NORTH:
            a = 1;
            b = 0;
            break;
         default:
            return false;
      }

      return confirmSquareCorners(a, b, quad);
   }

   private static boolean confirmSquareCorners(int aCoordinate, int bCoordinate, QuadView quad) {
      int flags = 0;

      for (int i = 0; i < 4; i++) {
         float a = quad.posByIndex(i, aCoordinate);
         float b = quad.posByIndex(i, bCoordinate);
         if (a <= 1.0E-4F) {
            if (b <= 1.0E-4F) {
               flags |= 1;
            } else {
               if (!(b >= 0.9999F)) {
                  return false;
               }

               flags |= 2;
            }
         } else {
            if (!(a >= 0.9999F)) {
               return false;
            }

            if (b <= 1.0E-4F) {
               flags |= 4;
            } else {
               if (!(b >= 0.9999F)) {
                  return false;
               }

               flags |= 8;
            }
         }
      }

      return flags == 15;
   }

   public static Direction lightFace(QuadView quad) {
      Vector3fc normal = quad.faceNormal();
      switch (longestAxis(normal)) {
         case X:
            return normal.x() > 0.0F ? Direction.EAST : Direction.WEST;
         case Y:
            return normal.y() > 0.0F ? Direction.UP : Direction.DOWN;
         case Z:
            return normal.z() > 0.0F ? Direction.SOUTH : Direction.NORTH;
         default:
            return Direction.UP;
      }
   }

   public static float min(float a, float b, float c, float d) {
      float x = a < b ? a : b;
      float y = c < d ? c : d;
      return x < y ? x : y;
   }

   public static float max(float a, float b, float c, float d) {
      float x = a > b ? a : b;
      float y = c > d ? c : d;
      return x > y ? x : y;
   }

   public static Axis longestAxis(Vector3fc vec) {
      return longestAxis(vec.x(), vec.y(), vec.z());
   }

   public static Axis longestAxis(float normalX, float normalY, float normalZ) {
      Axis result = Axis.Y;
      float longest = Math.abs(normalY);
      float a = Math.abs(normalX);
      if (a > longest) {
         result = Axis.X;
         longest = a;
      }

      return Math.abs(normalZ) > longest ? Axis.Z : result;
   }
}
