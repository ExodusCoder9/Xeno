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
import com.xeno.config.gui.widget.CyclingOptionWidget;
import com.xeno.config.gui.widget.OptionWidget;
import org.apache.commons.lang3.ArrayUtils;

public class CyclingOption<E> extends Option<E> {
   private E[] values;
   private int index;

   public CyclingOption(Component name, E[] values, Consumer<E> setter, Supplier<E> getter) {
      super(name, setter, getter);
      this.values = values;
      this.index = this.findNewValueIndex();
   }

   @Override
   protected OptionWidget<?> createWidget() {
      CyclingOptionWidget widget = new CyclingOptionWidget(this, this.name);
      this.widget = widget;
      return widget;
   }

   public void updateOption(E[] values, Consumer<E> setter, Supplier<E> getter) {
      this.onApply = setter;
      this.valueSupplier = getter;
      this.values = values;
      this.index = ArrayUtils.indexOf(this.values, this.getNewValue());
   }

   public int index() {
      return this.index;
   }

   public void setValues(E[] values) {
      this.values = values;
   }

   public void prevValue() {
      if (this.index > 0) {
         this.index--;
      }

      this.updateValue();
   }

   public void nextValue() {
      if (this.index < this.values.length - 1) {
         this.index++;
      }

      this.updateValue();
   }

   private void updateValue() {
      if (this.index >= 0 && this.index < this.values.length) {
         this.newValue = this.values[this.index];
         if (this.onChange != null) {
            this.onChange.run();
         }
      }
   }

   @Override
   public void setNewValue(E e) {
      super.setNewValue(e);
      this.index = this.findNewValueIndex();
   }

   private int findNewValueIndex() {
      for (int i = 0; i < this.values.length; i++) {
         if (this.values[i].equals(this.newValue)) {
            return i;
         }
      }

      return -1;
   }

   public E[] getValues() {
      return this.values;
   }
}
