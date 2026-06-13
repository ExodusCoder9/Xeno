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

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import com.xeno.config.gui.widget.OptionWidget;
import com.xeno.config.gui.widget.SwitchOptionWidget;

public class SwitchOption extends Option<Boolean> {
   public SwitchOption(Component name, Consumer<Boolean> setter, Supplier<Boolean> getter) {
      super(name, setter, getter, i -> Component.nullToEmpty(String.valueOf(i)));
   }

   @Override
   protected OptionWidget<?> createWidget() {
      SwitchOptionWidget widget = new SwitchOptionWidget(this, this.name);
      this.widget = widget;
      return widget;
   }
}
