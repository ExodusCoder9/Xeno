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


import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import com.xeno.config.gui.widget.OptionWidget;

public abstract class Option<T> {
   protected final Component name;
   protected Component tooltip;
   protected PerformanceImpact impact;
   protected Consumer<T> onApply;
   protected Supplier<T> valueSupplier;
   protected T value;
   protected T newValue;
   protected Function<T, Component> translator;
   protected Function<T, Component> tooltipTranslator;
   OptionWidget<?> widget;
   protected boolean active;
   protected Runnable onChange;
   protected Supplier<Boolean> activationFn;

   public Option(Component name, Consumer<T> setter, Supplier<T> getter, Function<T, Component> translator, Function<T, Component> tooltip) {
      this.name = name;
      this.onApply = setter;
      this.valueSupplier = getter;
      this.translator = translator;
      this.tooltipTranslator = tooltip;
      this.newValue = this.value = this.valueSupplier.get();
   }

   public Option(Component name, Consumer<T> setter, Supplier<T> getter, Function<T, Component> translator) {
      this.name = name;
      this.onApply = setter;
      this.valueSupplier = getter;
      this.newValue = this.value = this.valueSupplier.get();
      this.translator = translator;
   }

   public Option(Component name, Consumer<T> setter, Supplier<T> getter) {
      this.name = name;
      this.onApply = setter;
      this.valueSupplier = getter;
      this.newValue = this.value = this.valueSupplier.get();
   }

   public Option<T> setOnApply(Consumer<T> onApply) {
      this.onApply = onApply;
      return this;
   }

   public Option<T> setValueSupplier(Supplier<T> supplier) {
      this.valueSupplier = supplier;
      return this;
   }

   public Option<T> setTranslator(Function<T, Component> translator) {
      this.translator = translator;
      return this;
   }

   public Function<T, Component> getTranslator() {
      return this.translator;
   }

   public Option<T> setTooltip(Function<T, Component> tooltipTranslator) {
      this.tooltipTranslator = tooltipTranslator;
      return this;
   }

   public PerformanceImpact getImpact() {
      return this.impact;
   }

   public Option<T> setImpact(PerformanceImpact impact) {
      this.impact = impact;
      return this;
   }

   public Option<T> setActive(boolean active) {
      this.active = active;
      this.widget.active = active;
      return this;
   }

   protected abstract OptionWidget<?> createWidget();

   public OptionWidget<?> getWidget() {
      if (this.widget == null) {
         this.widget = this.createWidget();
      }

      return this.widget;
   }

   public void setNewValue(T t) {
      this.newValue = t;
      if (this.onChange != null) {
         this.onChange.run();
      }
   }

   public void updateActiveState() {
      if (this.activationFn != null) {
         this.active = this.activationFn.get();
      } else {
         this.active = true;
      }

      this.widget.setActive(this.active);
   }

   public Component getName() {
      return this.name;
   }

   public Option<T> setOnChange(Runnable runnable) {
      this.onChange = runnable;
      return this;
   }

   public Option<T> setActivationFn(Supplier<Boolean> activationFn) {
      this.activationFn = activationFn;
      return this;
   }

   public boolean isChanged() {
      return !this.newValue.equals(this.value);
   }

   public void apply() {
      this.onApply.accept(this.newValue);
      this.value = this.newValue;
   }

   public void resetValue() {
      this.setNewValue(this.value);
   }

   public T getNewValue() {
      return this.newValue;
   }

   public Component getDisplayedValue() {
      return this.translator.apply(this.newValue);
   }

   public Component getTooltip() {
      return this.tooltipTranslator != null ? this.tooltipTranslator.apply(this.newValue) : null;
   }
}
