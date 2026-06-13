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
package com.xeno.config.option;

import com.xeno.config.gui.OptionBlock;
import com.xeno.config.gui.VOptionList;

public class OptionPage {
   public final String name;
   public OptionBlock[] optionBlocks;
   private VOptionList optionList;
   private int order;

   public OptionPage(String name, OptionBlock[] optionBlocks) {
      this.name = name;
      this.optionBlocks = optionBlocks;
   }

   public void createList(int x, int y, int width, int height, int itemHeight) {
      this.optionList = new VOptionList(x, y, width, height, itemHeight);
      this.optionList.addAll(this.optionBlocks);
   }

   public VOptionList getOptionList() {
      return this.optionList;
   }

   public boolean optionChanged() {
      boolean changed = false;

      for (OptionBlock block : this.optionBlocks) {
         for (Option<?> option : block.options()) {
            if (option.isChanged()) {
               changed = true;
            }
         }
      }

      return changed;
   }

   public void applyOptionChanges() {
      for (OptionBlock block : this.optionBlocks) {
         for (Option<?> option : block.options()) {
            if (option.isChanged()) {
               option.apply();
            }
         }
      }
   }

   public void updateOptionStates() {
      for (OptionBlock block : this.optionBlocks) {
         for (Option<?> option : block.options()) {
            option.updateActiveState();
         }
      }
   }

   public void resetToOriginalState() {
      for (OptionBlock block : this.optionBlocks) {
         for (Option<?> option : block.options()) {
            option.resetValue();
         }
      }
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }
}
