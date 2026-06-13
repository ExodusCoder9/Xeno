package com.xeno.config.gui;

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


import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.xeno.Initializer;
import com.xeno.config.option.Options;

public class ModSettingsRegistry {
   public static final ModSettingsRegistry INSTANCE = new ModSettingsRegistry();
   private final Set<ModSettingsEntry> modEntries = new ObjectArraySet();

   ModSettingsRegistry() {
      ModSettingsEntry vulkanModSettings = new ModSettingsEntry(
         Component.literal("Xeno").withStyle(ChatFormatting.DARK_PURPLE),
         null,
         Options::getOptionPages,
         () -> Initializer.CONFIG.write()
      );
      this.addModEntry(vulkanModSettings);
   }

   public void addModEntry(ModSettingsEntry entry) {
      this.modEntries.add(entry);
   }

   public Set<ModSettingsEntry> getModEntries() {
      return this.modEntries;
   }
}
