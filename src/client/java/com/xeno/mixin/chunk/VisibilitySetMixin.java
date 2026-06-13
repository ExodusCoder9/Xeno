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
package com.xeno.mixin.chunk;

import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import com.xeno.interfaces.VisibilitySetExtended;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(VisibilitySet.class)
public class VisibilitySetMixin implements VisibilitySetExtended {
   private long vis = 0L;

   @Overwrite
   public void set(Direction dir1, Direction dir2, boolean p_112989_) {
      this.vis = this.vis | 1L << (dir1.ordinal() << 3) + dir2.ordinal() | 1L << (dir2.ordinal() << 3) + dir1.ordinal();
   }

   @Overwrite
   public void setAll(boolean bl) {
      if (bl) {
         this.vis = -1L;
      }
   }

   @Overwrite
   public boolean visibilityBetween(Direction dir1, Direction dir2) {
      return (this.vis & 1L << (dir1.ordinal() << 3) + dir2.ordinal()) != 0L;
   }

   @Override
   public long getVisibility() {
      return this.vis;
   }
}
