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
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.xeno.config.gui.widget.OptionWidget;
import com.xeno.config.gui.widget.RangeOptionWidget;

public class RangeOption extends Option<Integer> {
   int min;
   int max;
   int step;
   float scaledNewValue;

   public RangeOption(Component name, int min, int max, int step, Function<Integer, Component> translator, Consumer<Integer> setter, Supplier<Integer> getter) {
      super(name, setter, getter, translator);
      this.min = min;
      this.max = max;
      this.step = step;
      this.scaledNewValue = this.computeScaledValue(this.newValue.intValue());
   }

   public RangeOption(Component name, int min, int max, int step, Consumer<Integer> setter, Supplier<Integer> getter) {
      this(name, min, max, step, i -> Component.literal(String.valueOf(i)), setter, getter);
   }

   @Override
   protected OptionWidget<?> createWidget() {
      RangeOptionWidget widget = new RangeOptionWidget(this, this.name);
      this.widget = widget;
      return widget;
   }

   @Override
   public Component getName() {
      return Component.nullToEmpty(this.name.getString() + ": " + this.getNewValue().toString());
   }

   public float getScaledValue() {
      return this.scaledNewValue;
   }

   public void setNewValueFromScaledFloat(float f) {
      double n = Mth.lerp(f, this.min, this.max);
      n = this.step * Math.round(n / this.step);
      this.setNewValue((int)n);
   }

   public void setNewValue(Integer newValue) {
      super.setNewValue(newValue);
      this.scaledNewValue = this.computeScaledValue(this.newValue.intValue());
   }

   public float getScaledNewValue() {
      return this.scaledNewValue;
   }

   private float computeScaledValue(float value) {
      return (value - this.min) / (this.max - this.min);
   }
}
