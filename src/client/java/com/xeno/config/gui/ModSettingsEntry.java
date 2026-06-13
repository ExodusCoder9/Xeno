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


import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import com.xeno.config.option.OptionPage;

public class ModSettingsEntry {
   public final FormattedText modName;
   public final Supplier<Identifier> iconSupplier;
   private final Supplier<List<OptionPage>> optionPageSupplier;
   private final Runnable onApply;
   private Identifier icon;
   List<OptionPage> pages;

   public ModSettingsEntry(FormattedText modName, Supplier<Identifier> iconSupplier, Supplier<List<OptionPage>> optionPageSupplier, Runnable onApply) {
      this.modName = modName;
      this.iconSupplier = iconSupplier;
      this.optionPageSupplier = optionPageSupplier;
      this.onApply = onApply;
   }

   public List<OptionPage> initPages() {
      this.pages = this.optionPageSupplier.get();
      return this.pages;
   }

   public List<OptionPage> getPages() {
      return this.pages;
   }

    public Identifier getIcon() {
       if (this.icon == null && this.iconSupplier != null) {
          this.icon = this.iconSupplier.get();
       }

       return this.icon;
    }

   public void runOnApply() {
      this.onApply.run();
   }
}
