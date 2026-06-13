package com.xeno.config.option;

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


import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum PerformanceImpact {
   LOW(Component.translatable("xeno.options.performanceImpact.low").withStyle(ChatFormatting.DARK_GREEN)),
   MEDIUM(Component.translatable("xeno.options.performanceImpact.medium").withStyle(ChatFormatting.YELLOW)),
   HIGH(Component.translatable("xeno.options.performanceImpact.high").withStyle(ChatFormatting.DARK_PURPLE));

   private final Component component;

   PerformanceImpact(Component component) {
      this.component = component;
   }

   public Component component() {
      return this.component;
   }
}
