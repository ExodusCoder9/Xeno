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

import java.util.ArrayList;
import java.util.List;
import com.xeno.config.gui.OptionBlock;

public class Page {
   private final String name;
   private final List<Page.Block> blocks = new ArrayList<>();

   private Page(String name) {
      this.name = name;
   }

   public static Page of(String name) {
      return new Page(name);
   }

   public Page.Block block(String title) {
      Page.Block block = new Page.Block(title, this);
      this.blocks.add(block);
      return block;
   }

   public static class Block {
      private final String title;
      private final List<Option<?>> options = new ArrayList<>();
      private final Page parent;

      private Block(String title, Page parent) {
         this.title = title;
         this.parent = parent;
      }

      public Page.Block add(Option<?> option) {
         this.options.add(option);
         return this;
      }

      public Page done() {
         return this.parent;
      }

      private OptionBlock build() {
         return new OptionBlock(this.title, this.options.toArray(new Option[0]));
      }
   }
}
