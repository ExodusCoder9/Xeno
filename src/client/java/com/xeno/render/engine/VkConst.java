package com.xeno.render.engine;

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


import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;

public class VkConst {
   public VkConst() {
   }

   public static int of(AddressMode addressMode) {
      return switch (addressMode) {
         case REPEAT -> 0;
         case CLAMP_TO_EDGE -> 2;
         default -> throw new MatchException(null, null);
      };
   }

   public static int of(FilterMode filterMode) {
      return switch (filterMode) {
         case NEAREST -> 0;
         case LINEAR -> 1;
         default -> throw new MatchException(null, null);
      };
   }
}
