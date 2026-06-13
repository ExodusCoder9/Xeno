package com.xeno.config.gui.widget;

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


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.option.SwitchOption;
import com.xeno.vulkan.util.ColorUtil;

public class SwitchOptionWidget extends OptionWidget<SwitchOption> {
   private boolean focused;

   public SwitchOptionWidget(SwitchOption option, Component name) {
      super(option, name);
      this.updateDisplayedValue();
   }

   @Override
   protected void renderControls(double mouseX, double mouseY) {
      int center = this.controlX + this.controlWidth / 2;
      int halfWidth = 12;
      int x0 = center - halfWidth;
      int y0 = this.y + 4;
      int height = this.height - 8;
      int w1 = halfWidth - 4;
      int h1 = height - 4;
      if (this.option.getNewValue()) {
         int x1 = x0 + halfWidth + 2;
         int color = ColorUtil.ARGB.pack(0.4F, 0.4F, 0.4F, 1.0F);
         GuiRenderer.fillBox(x0 + 2, y0 + 2, x1 - (x0 + 2) - 1, h1, color);
         color = ColorUtil.ARGB.pack(1.0F, 1.0F, 1.0F, 1.0F);
         GuiRenderer.fillBox(x1, y0 + 2, w1, h1, color);
      } else {
         int color = ColorUtil.ARGB.pack(1.0F, 1.0F, 1.0F, 0.4F);
         GuiRenderer.fillBox(x0 + 2, y0 + 2, w1, h1, color);
      }

      int var19 = ColorUtil.ARGB.pack(0.6F, 0.6F, 0.6F, 1.0F);
      GuiRenderer.renderBoxBorder(x0, y0, halfWidth * 2, height, 1, var19);
      var19 = this.active ? -1 : -6250336;
      Font textRenderer = Minecraft.getInstance().font;
      int margin = Math.max(
         textRenderer.width(Component.translatable("options.on").getString()) / 3, textRenderer.width(Component.translatable("options.off").getString()) / 3
      );
      int x = this.controlX + this.controlWidth / 2 - (int)(halfWidth * 1.5F) - 4 - margin;
      int y = this.y + (this.height - 8) / 2;
      GuiRenderer.drawCenteredString(textRenderer, this.getDisplayedValue(), x, y, var19);
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
      this.option.setNewValue(!this.option.getNewValue());
      this.updateDisplayedValue();
   }

   @Override
   public void onRelease(double mouseX, double mouseY) {
   }

   @Override
   protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
   }

   @Override
   protected void updateDisplayedValue() {
      this.displayedValue = this.option.getNewValue() ? Component.translatable("options.on") : Component.translatable("options.off");
   }

   @Override
   public void setFocused(boolean bl) {
      this.focused = bl;
   }

   @Override
   public boolean isFocused() {
      return this.focused;
   }
}
