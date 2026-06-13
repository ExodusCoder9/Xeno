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
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.network.chat.Component;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.option.CyclingOption;
import com.xeno.render.shader.CustomRenderPipelines;
import com.xeno.vulkan.util.ColorUtil;
import org.jetbrains.annotations.NotNull;

public class CyclingOptionWidget extends OptionWidget<CyclingOption<?>> {
   private final CyclingOptionWidget.Button leftButton = new CyclingOptionWidget.Button(CyclingOptionWidget.Button.Direction.LEFT);
   private final CyclingOptionWidget.Button rightButton = new CyclingOptionWidget.Button(CyclingOptionWidget.Button.Direction.RIGHT);
   private boolean focused;

   public CyclingOptionWidget(CyclingOption<?> option, Component name) {
      super(option, name);
   }

   @Override
   public void setDimensions(int x, int y, int width, int height) {
      super.setDimensions(x, y, width, height);
      this.leftButton.setDimensions(this.controlX, 16);
      this.rightButton.setDimensions(this.controlX + this.controlWidth - 16, 16);
   }

   @Override
   public void renderControls(double mouseX, double mouseY) {
      this.renderBars();
      this.leftButton.setStatus(this.option.index() > 0);
      this.rightButton.setStatus(this.option.index() < this.option.getValues().length - 1);
      int color = this.active ? -1 : -6250336;
      Font textRenderer = Minecraft.getInstance().font;
      int x = this.controlX + this.controlWidth / 2;
      int y = this.y + (this.height - 9) / 2;
      GuiRenderer.drawScrollingString(
         textRenderer, this.getDisplayedValue(), x, y, this.rightButton.x - (this.leftButton.x + this.leftButton.width) - 12, color
      );
      this.leftButton.renderButton(mouseX, mouseY);
      this.rightButton.renderButton(mouseX, mouseY);
   }

   public void renderBars() {
      int count = this.option.getValues().length;
      int current = this.option.index();
      int margin = 30;
      int padding = 4;
      int barWidth = (this.controlWidth - 2 * margin - padding * count) / count;
      int color = ColorUtil.ARGB.pack(1.0F, 1.0F, 1.0F, 0.4F);
      int activeColor = ColorUtil.ARGB.pack(1.0F, 1.0F, 1.0F, 1.0F);
      if (barWidth > 0) {
         for (int i = 0; i < count; i++) {
            int x0 = this.controlX + margin + i * (barWidth + padding);
            int y0 = this.y + this.height - 5;
            int c = i == current ? activeColor : color;
            GuiRenderer.fill(x0, y0, x0 + barWidth, (int)(y0 + 1.5F), c);
         }
      }
   }

   @Override
   public void setActive(boolean active) {
      this.active = active;
      this.leftButton.active &= active;
      this.rightButton.active &= active;
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
      if (this.leftButton.isHovered(mouseX, mouseY)) {
         this.option.prevValue();
      } else if (this.rightButton.isHovered(mouseX, mouseY)) {
         this.option.nextValue();
      }
   }

   @Override
   public void onRelease(double mouseX, double mouseY) {
   }

   @Override
   protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
   }

   @Override
   public void setFocused(boolean bl) {
      this.focused = bl;
   }

   @Override
   public boolean isFocused() {
      return this.focused;
   }

   class Button {
      final int ACTIVE_COLOR = ColorUtil.ARGB.pack(1.0F, 1.0F, 1.0F, 0.8F);
      final int HOVERED_COLOR = ColorUtil.ARGB.pack(1.0F, 1.0F, 1.0F, 1.0F);
      final int INACTIVE_COLOR = ColorUtil.ARGB.pack(0.3F, 0.3F, 0.3F, 0.8F);
      int x;
      int width;
      boolean active = true;
      CyclingOptionWidget.Button.Direction direction;

      Button(CyclingOptionWidget.Button.Direction direction) {
         this.direction = direction;
      }

      public void setDimensions(int x, int width) {
         this.x = x;
         this.width = width;
      }

      boolean isHovered(double mouseX, double mouseY) {
         return mouseX >= this.x
            && mouseX <= this.x + this.width
            && mouseY >= CyclingOptionWidget.this.y
            && mouseY <= CyclingOptionWidget.this.y + CyclingOptionWidget.this.height;
      }

      void setStatus(boolean status) {
         this.active = status;
      }

      void renderButton(double mouseX, double mouseY) {
         float f = this.isHovered(mouseX, mouseY) && this.active ? 5.0F : 4.5F;
         int color;
         if (this.isHovered(mouseX, mouseY) && this.active) {
            color = this.HOVERED_COLOR;
         } else if (this.active) {
            color = this.ACTIVE_COLOR;
         } else {
            color = this.INACTIVE_COLOR;
         }

         float[][] vertices = this.getVertices(f);
         GuiRenderer.submitPolygon(CustomRenderPipelines.GUI_TRIANGLES, TextureSetup.noTexture(), vertices, color);
      }

      private float[] @NotNull [] getVertices(float f) {
         float w = f - 1.0F;
         float yC = CyclingOptionWidget.this.y + CyclingOptionWidget.this.height * 0.5F;
         float xC = this.x + this.width * 0.5F;
         float[][] vertices;
         if (this.direction == CyclingOptionWidget.Button.Direction.LEFT) {
            vertices = new float[][]{{xC - w, yC}, {xC + w, yC + f}, {xC + w, yC - f}};
         } else {
            vertices = new float[][]{{xC + w, yC}, {xC - w, yC - f}, {xC - w, yC + f}};
         }

         return vertices;
      }

      enum Direction {
         LEFT,
         RIGHT;

         Direction() {
         }
      }
   }
}
