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
package com.xeno.render.chunk.cull;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import com.xeno.render.vertex.format.I32_SNorm;
import org.joml.Vector3f;

public enum QuadFacing {
   X_POS,
   Y_POS,
   Z_POS,
   X_NEG,
   Z_NEG,
   UNDEFINED,
   Y_NEG;

   public static final QuadFacing[] VALUES = values();
   public static final int COUNT = VALUES.length;

   public static QuadFacing fromDirection(Direction direction) {
      return switch (direction) {
         case DOWN -> Y_NEG;
         case UP -> Y_POS;
         case NORTH -> Z_NEG;
         case SOUTH -> Z_POS;
         case WEST -> X_NEG;
         case EAST -> X_POS;
      };
   }

   public static QuadFacing fromNormal(int packedNormal) {
      float x = I32_SNorm.unpackX(packedNormal);
      float y = I32_SNorm.unpackY(packedNormal);
      float z = I32_SNorm.unpackZ(packedNormal);
      return fromNormal(x, y, z);
   }

   public static QuadFacing fromNormal(Vector3f normal) {
      return fromNormal(normal.x(), normal.y(), normal.z());
   }

   public static QuadFacing fromNormal(float x, float y, float z) {
      float absX = Math.abs(x);
      float absY = Math.abs(y);
      float absZ = Math.abs(z);
      float sum = absX + absY + absZ;
      if (Mth.equal(sum, 1.0F)) {
         if (Mth.equal(absX, 1.0F)) {
            return x > 0.0F ? X_POS : X_NEG;
         }

         if (Mth.equal(absY, 1.0F)) {
            return y > 0.0F ? Y_POS : Y_NEG;
         }

         if (Mth.equal(absZ, 1.0F)) {
            return z > 0.0F ? Z_POS : Z_NEG;
         }
      }

      return UNDEFINED;
   }
}
