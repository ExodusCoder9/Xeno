package com.xeno.config.gui;

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


import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

public abstract class GuiElement implements GuiEventListener, NarratableEntry {
   protected int width;
   protected int height;
   public int x;
   public int y;
   protected boolean hovered;
   protected long hoverStartTime;
   protected int hoverTime;
   protected long hoverStopTime;

   public GuiElement() {
   }

   public void setPosition(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public void setPosition(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
   }

   public void resize(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public void updateState(double mX, double mY) {
      if (this.isMouseOver(mX, mY)) {
         if (!this.hovered) {
            this.hoverStartTime = Util.getMillis();
         }

         this.hovered = true;
         this.hoverTime = (int)(Util.getMillis() - this.hoverStartTime);
      } else {
         if (this.hovered) {
            this.hoverStopTime = Util.getMillis();
         }

         this.hovered = false;
         this.hoverTime = 0;
      }
   }

   public float getHoverMultiplier(float time) {
      if (this.hovered) {
         return Math.min(this.hoverTime / time, 1.0F);
      }

      int delta = (int)(Util.getMillis() - this.hoverStopTime);
      return Math.max(1.0F - delta / time, 0.0F);
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX >= this.x && mouseY >= this.y && mouseX <= this.x + this.width && mouseY <= this.y + this.height;
   }

   public void setFocused(boolean bl) {
   }

   public boolean isFocused() {
      return false;
   }

   @NotNull
   public NarrationPriority narrationPriority() {
      return NarrationPriority.NONE;
   }

   public void updateNarration(NarrationElementOutput narrationElementOutput) {
   }
}
