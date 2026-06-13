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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import com.xeno.config.gui.GuiElement;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.gui.util.VGuiConstants;
import com.xeno.config.option.PerformanceImpact;
import com.xeno.vulkan.util.ColorUtil;

public abstract class VAbstractWidget extends GuiElement {
   public boolean active = true;
   public boolean visible = true;
   public boolean focused;
   protected Component message;
   protected boolean centeredText = true;
   protected int margin = 4;

   public VAbstractWidget() {
   }

   public void setDimensions(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
   }

   public void setTextLayout(boolean centered, int margin) {
      this.centeredText = centered;
      this.margin = margin;
   }

   public void render(double mX, double mY) {
      this.updateState(mX, mY);
      this.renderWidget(mX, mY);
   }

   public void renderWidget(double mX, double mY) {
   }

   public void onClick(double mX, double mY) {
   }

   public void onRelease(double mX, double mY) {
   }

   protected void onDrag(double mX, double mY, double f, double g) {
   }

   public void setActive(boolean active) {
      this.active = active;
   }

   protected void renderHovering(int xPadding, int yPadding) {
      if (!this.isFocused() && this.isActive() && this.visible && !this.focused) {
         float hoverMultiplier = this.getHoverMultiplier(200.0F);
         int borderColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_RED, hoverMultiplier);
         int backgroundColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_RED, 0.3F * hoverMultiplier);
         if (hoverMultiplier > 0.0F) {
            GuiRenderer.fill(this.x - xPadding, this.y - yPadding, this.x + this.width + xPadding, this.y + this.height + yPadding, backgroundColor);
            int x0 = this.x - xPadding;
            int x1 = this.x + this.width + xPadding;
            int y0 = this.y - yPadding;
            int y1 = this.y + this.height + yPadding;
            int border = 1;
            GuiRenderer.renderBorder(x0, y0, x1, y1, border, borderColor);
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
      if (this.active && this.visible && this.isValidClickButton(event.button())) {
         boolean clicked = this.clicked(event.x(), event.y());
         if (clicked) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onClick(event.x(), event.y());
            return true;
         }
      }

      return false;
   }

   protected boolean clicked(double mX, double mY) {
      return this.active && this.visible && mX >= this.getX() && mY >= this.getY() && mX < this.getX() + this.getWidth() && mY < this.getY() + this.getHeight();
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.isValidClickButton(event.button())) {
         this.onRelease(event.x(), event.y());
         return true;
      } else {
         return false;
      }
   }

   protected boolean isValidClickButton(int button) {
      return button == 0;
   }

   public boolean mouseDragged(MouseButtonEvent event, double d, double e) {
      if (this.isValidClickButton(event.button())) {
         this.onDrag(event.x(), event.y(), d, e);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void updateState(double mX, double mY) {
      super.updateState(mX, mY);
   }

   public void playDownSound(SoundManager soundManager) {
      soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   public Component getTooltip() {
      return null;
   }

   public PerformanceImpact getImpact() {
      return null;
   }
}
