package com.xeno.interfaces;

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


import com.mojang.blaze3d.vertex.VertexConsumer;

public interface ExtendedVertexBuilder {
   static ExtendedVertexBuilder of(VertexConsumer vertexConsumer) {
      return vertexConsumer instanceof ExtendedVertexBuilder ? (ExtendedVertexBuilder)vertexConsumer : null;
   }

   default boolean canUseFastVertex() {
      return true;
   }

   void vertex(float var1, float var2, float var3, int var4, float var5, float var6, int var7, int var8, int var9);

   default void vertex(float x, float y, float z, float u, float v, int packedColor, int light) {
   }
}
