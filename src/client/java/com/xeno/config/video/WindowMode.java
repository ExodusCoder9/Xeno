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
package com.xeno.config.video;

public enum WindowMode {
   WINDOWED(0),
   WINDOWED_FULLSCREEN(1),
   EXCLUSIVE_FULLSCREEN(2);

   public final int mode;

   WindowMode(int mode) {
      this.mode = mode;
   }

   public static WindowMode fromValue(int value) {
      return switch (value) {
         case 0 -> WINDOWED;
         case 1 -> WINDOWED_FULLSCREEN;
         case 2 -> EXCLUSIVE_FULLSCREEN;
         default -> throw new IllegalStateException("Unexpected value: " + value);
      };
   }

   public static String getComponentName(WindowMode windowMode) {
      return switch (windowMode) {
         case WINDOWED -> "Xeno.options.windowMode.windowed";
         case WINDOWED_FULLSCREEN -> "Xeno.options.windowMode.windowedFullscreen";
         case EXCLUSIVE_FULLSCREEN -> "options.fullscreen";
      };
   }
}
