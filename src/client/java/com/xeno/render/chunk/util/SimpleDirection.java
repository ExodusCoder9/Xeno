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
package com.xeno.render.chunk.util;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

public enum SimpleDirection {
   DOWN(0, 1, -1, new Vec3i(0, -1, 0)),
   UP(1, 0, -1, new Vec3i(0, 1, 0)),
   NORTH(2, 3, 2, new Vec3i(0, 0, -1)),
   SOUTH(3, 2, 0, new Vec3i(0, 0, 1)),
   WEST(4, 5, 1, new Vec3i(-1, 0, 0)),
   EAST(5, 4, 3, new Vec3i(1, 0, 0));

   private static final SimpleDirection[] VALUES = values();
   private final int data3d;
   private final int oppositeIndex;
   private final int data2d;
   public final byte nx;
   public final byte ny;
   public final byte nz;

   public static SimpleDirection of(Direction direction) {
      return VALUES[direction.get3DDataValue()];
   }

   SimpleDirection(int j, int k, int l, Vec3i normal) {
      this.data3d = j;
      this.oppositeIndex = k;
      this.data2d = l;
      this.nx = (byte)normal.getX();
      this.ny = (byte)normal.getY();
      this.nz = (byte)normal.getZ();
   }

   public int get3DDataValue() {
      return this.data3d;
   }

   public byte getStepX() {
      return this.nx;
   }

   public byte getStepY() {
      return this.ny;
   }

   public byte getStepZ() {
      return this.nz;
   }
}
