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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import com.xeno.config.gui.render.GuiRenderer;
import com.xeno.config.gui.util.VGuiConstants;
import com.xeno.vulkan.util.ColorUtil;

public class ModIconWidget extends VAbstractWidget {
   final FormattedText name;
   final Identifier icon;

   public ModIconWidget(FormattedText name, Identifier icon, int x0, int y0, int width, int height) {
      this.name = name;
      this.icon = icon;
      this.x = x0;
      this.y = y0;
      this.width = width;
      this.height = height;
   }

   @Override
   public void render(double mX, double mY) {
      int backgroundColor = ColorUtil.ARGB.multiplyAlpha(VGuiConstants.COLOR_BLACK, 0.6F);
      int width = this.width;
      int height = this.height;
      GuiRenderer.fill(this.x, this.y, this.x + width, this.y + height, backgroundColor);
      int textOffset = 6;
      if (this.icon != null) {
         int size = this.height - 4;
         int iconX = this.x + 4;
         int iconY = this.y + (height - size) / 2;
         GuiRenderer.guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.icon, iconX, iconY, 0.0F, 0.0F, size, size, size, size);
         textOffset = 6 + this.height;
      }
      GuiRenderer.drawString(Minecraft.getInstance().font, (Component)this.name, this.x + textOffset, this.y + this.height / 2 - 4, -1);
   }
}
