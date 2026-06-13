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
package com.xeno.render.model.quad;

import net.minecraft.core.Direction;
import com.xeno.render.chunk.cull.QuadFacing;

public interface ModelQuadView {
   int getFlags();

   float getX(int var1);

   float getY(int var1);

   float getZ(int var1);

   int getColor(int var1);

   float getU(int var1);

   float getV(int var1);

   int getColorIndex();

   Direction getFacingDirection();

   Direction lightFace();

   QuadFacing getQuadFacing();

   int getNormal();

   default boolean isTinted() {
      return this.getColorIndex() != -1;
   }
}
