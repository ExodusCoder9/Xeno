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

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.gui.util.VGuiConstants;
import com.xeno.vulkan.util.ColorUtil;
import org.jetbrains.annotations.Nullable;

public class VButtonWidget extends VAbstractWidget {
   boolean selected = false;
   Consumer<VButtonWidget> onPress;
   float alpha = 1.0F;

   public VButtonWidget(int x, int y, int width, int height, Component message, Consumer<VButtonWidget> onPress) {
      this.setPosition(x, y, width, height);
      this.message = message;
      this.onPress = onPress;
   }

   @Override
   public void renderWidget(double mouseX, double mouseY) {
      if (this.isVisible()) {
         int backgroundColor = this.isActive()
            ? ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_BLACK, 0.45F)
            : ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_BLACK, 0.3F);
         int textColor = this.isActive() ? VGuiConstants.COLOR_WHITE : VGuiConstants.COLOR_GRAY;
         int selectionOutlineColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_RED, 0.8F);
         int selectionFillColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_RED, 0.2F);
         GuiRenderer.fill(this.x, this.y, this.x + this.width, this.y + this.height, backgroundColor);
         if (this.selected) {
            GuiRenderer.fill(this.x, this.y, this.x + 2, this.y + this.height, selectionOutlineColor);
            GuiRenderer.fill(this.x, this.y, this.x + this.width, this.y + this.height, selectionFillColor);
         }

         this.renderHovering(0, 0);
         if (this.centeredText) {
            int var10002 = this.x + this.width / 2;
            GuiRenderer.drawCenteredString(
               Minecraft.getInstance().font, this.message, var10002, this.y + this.height / 2 - 4, textColor | Mth.ceil(this.alpha * 255.0F) << 24
            );
         } else {
            int var9 = this.x + this.margin;
            GuiRenderer.drawString(
               Minecraft.getInstance().font, this.message, var9, this.y + this.height / 2 - 4, textColor | Mth.ceil(this.alpha * 255.0F) << 24
            );
         }
      }
   }

   @Override
   public void onClick(double mX, double mY) {
      this.onPress.accept(this);
   }

   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   public boolean isVisible() {
      return this.visible;
   }

   @Override
   public boolean isActive() {
      return this.active;
   }

   @Override
   public void setActive(boolean active) {
      this.active = active;
   }

   @Nullable
   @Override
   public ComponentPath nextFocusPath(FocusNavigationEvent event) {
      return this.active && this.visible ? super.nextFocusPath(event) : null;
   }
}
