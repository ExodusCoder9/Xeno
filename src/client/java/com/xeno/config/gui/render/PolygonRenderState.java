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
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record PolygonRenderState(
   RenderPipeline pipeline,
   TextureSetup textureSetup,
   Matrix3x2f pose,
   float[][] vertices,
   int col,
   @Nullable ScreenRectangle scissorArea,
   @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
   public PolygonRenderState(
      RenderPipeline renderPipeline, TextureSetup textureSetup, Matrix3x2f pose, float[][] vertices, int color, @Nullable ScreenRectangle screenRectangle
   ) {
      this(renderPipeline, textureSetup, pose, vertices, color, screenRectangle, getBounds(vertices, pose, screenRectangle));
   }

   public PolygonRenderState {
   }

   public void buildVertices(VertexConsumer vertexConsumer) {
      for (float[] vertex : this.vertices) {
         float x = vertex[0];
         float y = vertex[1];
         vertexConsumer.addVertexWith2DPose(this.pose(), x, y).setColor(this.col);
      }
   }

   @Nullable
   private static ScreenRectangle getBounds(float[][] vertices, Matrix3x2f matrix3x2f, @Nullable ScreenRectangle screenRectangle) {
      float x0 = vertices[0][0];
      float x1 = vertices[0][0];
      float y0 = vertices[0][1];
      float y1 = vertices[0][1];

      for (float[] vertex : vertices) {
         float x = vertex[0];
         float y = vertex[1];
         if (x < x0) {
            x0 = x;
         }

         if (x > x1) {
            x1 = x;
         }

         if (y < y0) {
            y0 = y;
         }

         if (y > y1) {
            y1 = y;
         }
      }

      ScreenRectangle screenRectangle2 = new ScreenRectangle((int)x0, (int)y0, (int)(x1 - x0), (int)(y1 - y0)).transformMaxBounds(matrix3x2f);
      return screenRectangle != null ? screenRectangle.intersection(screenRectangle2) : screenRectangle2;
   }
}
