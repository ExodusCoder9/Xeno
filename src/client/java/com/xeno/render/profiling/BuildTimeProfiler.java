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
package com.xeno.render.profiling;

import net.minecraft.client.Minecraft;

public abstract class BuildTimeProfiler {
   private static boolean bench = false;
   private static long startTime;
   private static float deltaTime;

   public static void runBench(boolean building) {
      if (bench) {
         if (startTime == 0L) {
            startTime = System.nanoTime();
         }

         if (!building) {
            deltaTime = (float)(System.nanoTime() - startTime) * 1.0E-6F;
            bench = false;
            startTime = 0L;
         }
      }
   }

   public static void startBench() {
      bench = true;
      Minecraft.getInstance().levelRenderer.allChanged();
   }

   public static float getDeltaTime() {
      return deltaTime;
   }
}
