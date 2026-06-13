package com.xeno.config.gui.render;

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


import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;

public abstract class GuiRenderer {
   public static Minecraft minecraft;
   public static GuiGraphicsExtractor guiGraphics;
   public static PoseStack pose;
   public static BufferBuilder bufferBuilder;

   public GuiRenderer() {
   }

   public static void enableScissor(int i, int j, int k, int l) {
      guiGraphics.enableScissor(i, j, k, l);
   }

   public static void disableScissor() {
      guiGraphics.disableScissor();
   }

   public static void fillBox(int x0, int y0, int width, int height, int color) {
      fill(x0, y0, x0 + width, y0 + height, 0, color);
   }

   public static void fill(int x0, int y0, int x1, int y1, int color) {
      fill(x0, y0, x1, y1, 0, color);
   }

   public static void fill(int x0, int y0, int x1, int y1, int z, int color) {
      guiGraphics.fill(x0, y0, x1, y1, color);
   }

   public static void fillGradient(int x0, int y0, int x1, int y1, int color1, int color2) {
      fillGradient(x0, y0, x1, y1, 0, color1, color2);
   }

   public static void fillGradient(int x0, int y0, int x1, int y1, int z, int color1, int color2) {
      guiGraphics.fillGradient(x0, y0, x1, y1, color1, color2);
   }

   public static void renderBoxBorder(int x0, int y0, int width, int height, int borderWidth, int color) {
      renderBorder(x0, y0, x0 + width, y0 + height, borderWidth, color);
   }

   public static void renderBorder(int x0, int y0, int x1, int y1, int width, int color) {
      fill(x0, y0, x1, y0 + width, color);
      fill(x0, y1 - width, x1, y1, color);
      fill(x0, y0 + width, x0 + width, y1 - width, color);
      fill(x1 - width, y0 + width, x1, y1 - width, color);
   }

   public static void drawString(Font font, Component component, int x, int y, int color) {
      drawString(font, component.getVisualOrderText(), x, y, color);
   }

   public static void drawString(Font font, FormattedCharSequence formattedCharSequence, int x, int y, int color) {
      guiGraphics.text(font, formattedCharSequence, x, y, color);
   }

   public static void drawString(Font font, Component component, int x, int y, int color, boolean shadow) {
      drawString(font, component.getVisualOrderText(), x, y, color, shadow);
   }

   public static void drawString(Font font, FormattedCharSequence formattedCharSequence, int x, int y, int color, boolean shadow) {
      guiGraphics.text(font, formattedCharSequence, x, y, color, shadow);
   }

   public static void drawCenteredString(Font font, Component component, int x, int y, int color) {
      FormattedCharSequence formattedCharSequence = component.getVisualOrderText();
      guiGraphics.text(font, formattedCharSequence, x - font.width(formattedCharSequence) / 2, y, color);
   }

   public static void drawScrollingString(Font font, Component component, int x, int y, int maxWidth, int color) {
      int textWidth = font.width(component);
      if (textWidth <= maxWidth) {
         drawCenteredString(font, component, x, y, color);
      } else {
         int x0 = x - maxWidth / 2;
         int x1 = x + maxWidth / 2;
         int scrollAmount = textWidth - maxWidth;
         double currentTimeInSeconds = Util.getMillis() / 1000.0;
         double scrollSpeed = Math.max(scrollAmount * 0.5, 3.0);
         double scrollingOffset = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * currentTimeInSeconds / scrollSpeed)) / 2.0 + 0.5;
         double horizontalScroll = Mth.lerp(scrollingOffset, 0.0, scrollAmount);
         enableScissor(x0 - 1, 0, x1, Minecraft.getInstance().getWindow().getScreenHeight());
         drawString(font, component, (int)(x0 - horizontalScroll), y, color);
         disableScissor();
      }
   }

   public static int getMaxTextWidth(Font font, List<FormattedCharSequence> list) {
      int maxWidth = 0;

      for (FormattedCharSequence text : list) {
         int width = font.width(text);
         if (width > maxWidth) {
            maxWidth = width;
         }
      }

      return maxWidth;
   }

   public static void submitPolygon(RenderPipeline renderPipeline, TextureSetup textureSetup, float[][] vertices, int color) {
      guiGraphics.guiRenderState
         .addGuiElement(new PolygonRenderState(renderPipeline, textureSetup, new Matrix3x2f(), vertices, color, guiGraphics.scissorStack.peek()));
   }
}
