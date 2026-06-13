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
package com.xeno.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.option.Option;
import com.xeno.config.option.PerformanceImpact;
import com.xeno.vulkan.util.ColorUtil;
import org.jetbrains.annotations.NotNull;

public abstract class OptionWidget<O extends Option<?>> extends VAbstractWidget implements NarratableEntry {
   public int controlX;
   public int controlWidth;
   private final Component name;
   protected Component displayedValue;
   protected boolean controlHovered;
   final O option;

   public OptionWidget(O option, Component name) {
      this.option = option;
      this.name = name;
      this.displayedValue = Component.literal("N/A");
   }

   @Override
   public void setDimensions(int x, int y, int width, int height) {
      super.setDimensions(x, y, width, height);
      this.controlWidth = Math.min((int)(width * 0.5F) - 8, 120);
      this.controlX = this.x + this.width - this.controlWidth - 8;
   }

   @Override
   public void render(double mouseX, double mouseY) {
      if (this.visible) {
         this.updateDisplayedValue();
         this.controlHovered = mouseX >= this.controlX && mouseY >= this.y && mouseX < this.controlX + this.controlWidth && mouseY < this.y + this.height;
         this.renderWidget(mouseX, mouseY);
      }
   }

   @Override
   public void renderWidget(double mouseX, double mouseY) {
      Minecraft minecraftClient = Minecraft.getInstance();
      int xPadding = 0;
      int yPadding = 0;
      int color = ColorUtil.ARGB.pack(0.0F, 0.0F, 0.0F, 0.45F);
      GuiRenderer.fill(this.x - xPadding, this.y - yPadding, this.x + this.width + xPadding, this.y + this.height + yPadding, color);
      this.renderHovering(0, 0);
      color = this.active ? -1 : -6250336;
      Font textRenderer = minecraftClient.font;
      Component nameComp = this.getName();
      if (this.option.isChanged()) {
         nameComp = nameComp.copy().withStyle(style -> style.withItalic(true));
      }

      GuiRenderer.drawString(textRenderer, nameComp.getVisualOrderText(), this.x + 8, this.y + (this.height - 8) / 2, color);
      this.renderControls(mouseX, mouseY);
   }

   protected abstract void renderControls(double var1, double var3);

   @Override
   public abstract void onClick(double var1, double var3);

   @Override
   public abstract void onRelease(double var1, double var3);

   @Override
   protected abstract void onDrag(double var1, double var3, double var5, double var7);

   @Override
   public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
      if (this.isValidClickButton(event.button())) {
         this.onDrag(event.x(), event.y(), deltaX, deltaY);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
      if (this.active && this.visible) {
         if (this.isValidClickButton(event.button()) && this.clicked(event.x(), event.y())) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onClick(event.x(), event.y());
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.isValidClickButton(event.button())) {
         this.onRelease(event.x(), event.y());
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return this.active && this.visible && mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
   }

   @Override
   public void setFocused(boolean bl) {
      this.focused = bl;
   }

   @Override
   public boolean isFocused() {
      return this.focused;
   }

   @Override
   protected boolean clicked(double mouseX, double mouseY) {
      return this.active && this.visible && mouseX >= this.controlX && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
   }

   public Component getName() {
      return this.name;
   }

   public Component getDisplayedValue() {
      return this.displayedValue;
   }

   protected void updateDisplayedValue() {
      this.displayedValue = this.option.getDisplayedValue();
   }

   @Override
   public Component getTooltip() {
      return this.option.getTooltip();
   }

   @Override
   public PerformanceImpact getImpact() {
      return this.option.getImpact();
   }

   @NotNull
   @Override
   public NarratableEntry.NarrationPriority narrationPriority() {
      if (this.focused) {
         return NarratableEntry.NarrationPriority.FOCUSED;
      } else {
         return this.hovered ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
      }
   }

   @Override
   public final void updateNarration(NarrationElementOutput narrationElementOutput) {
   }
}
