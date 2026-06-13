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
package com.xeno.render.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.shader.GraphicsPipeline;
import com.xeno.vulkan.texture.VTextureSelector;
import com.xeno.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

public class DrawUtil {
   public static void blitQuad() {
      blitQuad(0.0F, 1.0F, 1.0F, 0.0F);
   }

   public static void drawTexQuad(BufferBuilder builder, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1) {
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      bufferBuilder.addVertex(x0, y0, z).setUv(0.0F, 1.0F);
      bufferBuilder.addVertex(x1, y0, z).setUv(1.0F, 1.0F);
      bufferBuilder.addVertex(x1, y1, z).setUv(1.0F, 0.0F);
      bufferBuilder.addVertex(x0, y1, z).setUv(0.0F, 0.0F);
      MeshData meshData = bufferBuilder.buildOrThrow();
      Renderer.getDrawer().draw(meshData.vertexBuffer(), VertexFormat.Mode.QUADS, meshData.drawState().format(), meshData.drawState().vertexCount());
   }

   public static void blitQuad(float x0, float y0, float x1, float y1) {
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      bufferBuilder.addVertex(x0, y0, 0.0F).setUv(0.0F, 1.0F);
      bufferBuilder.addVertex(x1, y0, 0.0F).setUv(1.0F, 1.0F);
      bufferBuilder.addVertex(x1, y1, 0.0F).setUv(1.0F, 0.0F);
      bufferBuilder.addVertex(x0, y1, 0.0F).setUv(0.0F, 0.0F);
      MeshData meshData = bufferBuilder.buildOrThrow();
      Renderer.getDrawer().draw(meshData.vertexBuffer(), VertexFormat.Mode.QUADS, meshData.drawState().format(), meshData.drawState().vertexCount());
   }

   public static void drawFramebuffer(GraphicsPipeline pipeline, VulkanImage attachment) {
      Renderer.getInstance().bindGraphicsPipeline(pipeline);
      VTextureSelector.bindTexture(attachment);
      Matrix4f projection = new Matrix4f().setOrtho(0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F, true);
      Matrix4fStack poseStack = RenderSystem.getModelViewStack();
      poseStack.pushMatrix();
      poseStack.identity();
      VRenderSystem.applyMVP(poseStack, projection);
      poseStack.popMatrix();
      Renderer.getInstance().uploadAndBindUBOs(pipeline);
      blitQuad(0.0F, 0.0F, 1.0F, 1.0F);
   }
}
