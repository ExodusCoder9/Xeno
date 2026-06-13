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
package com.xeno.render.sky;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import com.xeno.render.VBO;
import com.xeno.render.shader.PipelineManager;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.shader.GraphicsPipeline;
import com.xeno.vulkan.util.ColorUtil;
import org.apache.commons.lang3.Validate;
import org.joml.Matrix4fStack;

public class CloudRenderer {
   private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/environment/clouds.png");
   private static final int DIR_NEG_Y_BIT = 1;
   private static final int DIR_POS_Y_BIT = 2;
   private static final int DIR_NEG_X_BIT = 4;
   private static final int DIR_POS_X_BIT = 8;
   private static final int DIR_NEG_Z_BIT = 16;
   private static final int DIR_POS_Z_BIT = 32;
   private static final byte Y_BELOW_CLOUDS = 0;
   private static final byte Y_ABOVE_CLOUDS = 1;
   private static final byte Y_INSIDE_CLOUDS = 2;
   private static final int CELL_WIDTH = 12;
   private static final int CELL_HEIGHT = 4;
   private CloudRenderer.CloudGrid cloudGrid;
   private int prevCloudX;
   private int prevCloudZ;
   private byte prevCloudY;
   private CloudStatus prevCloudsStatus;
   private boolean generateClouds;
   private VBO cloudBuffer;

   public CloudRenderer() {
      this.loadTexture();
   }

   public void loadTexture() {
      this.cloudGrid = createCloudGrid(TEXTURE_LOCATION);
   }

   public void renderClouds(float cloudHeight, int cloudColor, double camX, double camY, double camZ, long gameTime, float partialTicks) {
      Minecraft minecraft = Minecraft.getInstance();
      float timeOffset = (float)(gameTime % (this.cloudGrid.width * 400L)) + partialTicks;
      double centerX = camX + timeOffset * 0.03F;
      double centerZ = camZ + 3.96F;
      double centerY = cloudHeight - (float)camY + 0.33F;
      int centerCellX = (int)Math.floor(centerX / 12.0);
      int centerCellZ = (int)Math.floor(centerZ / 12.0);
      byte yState;
      if (centerY < -4.0) {
         yState = 0;
      } else if (centerY > 0.0) {
         yState = 1;
      } else {
         yState = 2;
      }

      CloudStatus cloudStatus = minecraft.options.cloudStatus().get();
      if (centerCellX != this.prevCloudX
         || centerCellZ != this.prevCloudZ
         || cloudStatus != this.prevCloudsStatus
         || this.prevCloudY != yState
         || this.cloudBuffer == null) {
         this.prevCloudX = centerCellX;
         this.prevCloudZ = centerCellZ;
         this.prevCloudsStatus = cloudStatus;
         this.prevCloudY = yState;
         this.generateClouds = true;
      }

      if (this.generateClouds) {
         this.generateClouds = false;
         if (this.cloudBuffer != null) {
            this.cloudBuffer.close();
         }

         this.resetBuffer();
         MeshData cloudsMesh = this.buildClouds(Tesselator.getInstance(), centerCellX, centerCellZ, centerY);
         if (cloudsMesh == null) {
            return;
         }

         this.cloudBuffer = new VBO(true);
         this.cloudBuffer.upload(cloudsMesh);
      }

      if (this.cloudBuffer != null) {
         float xTranslation = (float)(centerX - centerCellX * 12);
         float yTranslation = (float)centerY;
         float zTranslation = (float)(centerZ - centerCellZ * 12);
         Renderer.getInstance().getMainPass().rebindMainTarget();
         Matrix4fStack poseStack = RenderSystem.getModelViewStack();
         poseStack.pushMatrix();
         poseStack.translate(-xTranslation, yTranslation, -zTranslation);
         VRenderSystem.applyModelViewMatrix(poseStack);
         VRenderSystem.calculateMVP();
         VRenderSystem.setModelOffset(-xTranslation, 0.0F, -zTranslation);
         float r = ColorUtil.ARGB.unpackR(cloudColor);
         float g = ColorUtil.ARGB.unpackG(cloudColor);
         float b = ColorUtil.ARGB.unpackB(cloudColor);
         VRenderSystem.setShaderColor(r, g, b, 0.8F);
         GraphicsPipeline pipeline = PipelineManager.getCloudsPipeline();
         VRenderSystem.enableBlend();
         VRenderSystem.blendFuncSeparate(770, 771, 1, 0);
         VRenderSystem.enableDepthTest();
         VRenderSystem.depthFunc(515);
         GlStateManager._enableDepthTest();
         GlStateManager._depthMask(true);
         GlStateManager._colorMask(15);
         GlStateManager._disablePolygonOffset();
         VRenderSystem.setPolygonModeGL(6914);
         VRenderSystem.setPrimitiveTopologyGL(4);
         boolean fastClouds = this.prevCloudsStatus == CloudStatus.FAST;
         boolean insideClouds = yState == 2;
         boolean disableCull = insideClouds || fastClouds && centerY <= 0.0;
         if (disableCull) {
            VRenderSystem.disableCull();
         } else {
            VRenderSystem.enableCull();
         }

         if (!fastClouds) {
            VRenderSystem.colorMask(false, false, false, false);
            this.cloudBuffer.bind(pipeline);
            this.cloudBuffer.draw();
            VRenderSystem.colorMask(true, true, true, true);
         }

         this.cloudBuffer.bind(pipeline);
         this.cloudBuffer.draw();
         poseStack.popMatrix();
         VRenderSystem.enableCull();
         VRenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         VRenderSystem.setModelOffset(0.0F, 0.0F, 0.0F);
      }
   }

   public void resetBuffer() {
      if (this.cloudBuffer != null) {
         this.cloudBuffer.close();
         this.cloudBuffer = null;
      }
   }

   private MeshData buildClouds(Tesselator tesselator, int centerCellX, int centerCellZ, double cloudY) {
      float upFaceBrightness = 1.0F;
      float xDirBrightness = 0.9F;
      float downFaceBrightness = 0.7F;
      float zDirBrightness = 0.8F;
      BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      int cloudRange = Math.min(Minecraft.getInstance().options.cloudRange().get(), 128) * 16;
      int renderDistance = Mth.ceil(cloudRange / 12.0F);
      boolean insideClouds = this.prevCloudY == 2;
      if (this.prevCloudsStatus == CloudStatus.FANCY) {
         for (int cellX = -renderDistance; cellX < renderDistance; cellX++) {
            for (int cellZ = -renderDistance; cellZ < renderDistance; cellZ++) {
               int cellIdx = this.cloudGrid.getWrappedIdx(centerCellX + cellX, centerCellZ + cellZ);
               byte renderFaces = this.cloudGrid.renderFaces[cellIdx];
               int baseColor = this.cloudGrid.pixels[cellIdx];
               float x = cellX * 12;
               float z = cellZ * 12;
               if ((renderFaces & 2) != 0 && cloudY <= 0.0) {
                  int color = ColorUtil.ARGB.multiplyRGB(baseColor, 1.0F);
                  putVertex(bufferBuilder, x + 12.0F, 4.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 4.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 4.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 4.0F, z + 12.0F, color);
               }

               if ((renderFaces & 1) != 0 && cloudY >= -4.0) {
                  int color = ColorUtil.ARGB.multiplyRGB(baseColor, 0.7F);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 12.0F, color);
               }

               if ((renderFaces & 8) != 0 && (x < 1.0F || insideClouds)) {
                  int color = ColorUtil.ARGB.multiplyRGB(baseColor, 0.9F);
                  putVertex(bufferBuilder, x + 12.0F, 4.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 4.0F, z + 0.0F, color);
               }

               if ((renderFaces & 4) != 0 && (x > -1.0F || insideClouds)) {
                  int color = ColorUtil.ARGB.multiplyRGB(baseColor, 0.9F);
                  putVertex(bufferBuilder, x + 0.0F, 4.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 4.0F, z + 12.0F, color);
               }

               if ((renderFaces & 32) != 0 && (z < 1.0F || insideClouds)) {
                  int color = ColorUtil.ARGB.multiplyRGB(baseColor, 0.8F);
                  putVertex(bufferBuilder, x + 0.0F, 4.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 4.0F, z + 12.0F, color);
               }

               if ((renderFaces & 16) != 0 && (z > -1.0F || insideClouds)) {
                  int color = ColorUtil.ARGB.multiplyRGB(baseColor, 0.8F);
                  putVertex(bufferBuilder, x + 12.0F, 4.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 4.0F, z + 0.0F, color);
               }
            }
         }
      } else {
         for (int cellX = -renderDistance; cellX < renderDistance; cellX++) {
            for (int cellZ = -renderDistance; cellZ < renderDistance; cellZ++) {
               int cellIdx = this.cloudGrid.getWrappedIdx(centerCellX + cellX, centerCellZ + cellZ);
               byte renderFaces = this.cloudGrid.renderFaces[cellIdx];
               int baseColor = this.cloudGrid.pixels[cellIdx];
               float x = cellX * 12;
               float z = cellZ * 12;
               if ((renderFaces & 1) != 0) {
                  int color = ColorUtil.ARGB.multiplyRGB(baseColor, 1.0F);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 12.0F, color);
                  putVertex(bufferBuilder, x + 0.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 0.0F, color);
                  putVertex(bufferBuilder, x + 12.0F, 0.0F, z + 12.0F, color);
               }
            }
         }
      }

      return bufferBuilder.build();
   }

   private static void putVertex(BufferBuilder bufferBuilder, float x, float y, float z, int color) {
      bufferBuilder.addVertex(x, y, z).setColor(color);
   }

   private static CloudRenderer.CloudGrid createCloudGrid(Identifier textureLocation) {
      ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

      try {
         Resource resource = resourceManager.getResourceOrThrow(textureLocation);

         try (InputStream inputStream = resource.open()) {
            NativeImage image = NativeImage.read(inputStream);
            int width = image.getWidth();
            int height = image.getHeight();
            Validate.isTrue(width == height, "Image width and height must be the same", new Object[0]);
            int[] pixels = image.getPixelsABGR();
            return new CloudRenderer.CloudGrid(pixels, width);
         }
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   static class CloudGrid {
      final int width;
      final int[] pixels;
      final byte[] renderFaces;

      CloudGrid(int[] pixels, int width) {
         this.pixels = pixels;
         this.width = width;
         this.renderFaces = this.computeRenderFaces();
      }

      byte[] computeRenderFaces() {
         byte[] renderFaces = new byte[this.pixels.length];

         for (int z = 0; z < this.width; z++) {
            for (int x = 0; x < this.width; x++) {
               int idx = this.getIdx(x, z);
               int pixel = this.pixels[idx];
               if (hasColor(pixel)) {
                  byte faces = 3;
                  int adjPixel = this.getTexelWrapped(x - 1, z);
                  if (pixel != adjPixel) {
                     faces = (byte)(faces | 4);
                  }

                  adjPixel = this.getTexelWrapped(x + 1, z);
                  if (pixel != adjPixel) {
                     faces = (byte)(faces | 8);
                  }

                  adjPixel = this.getTexelWrapped(x, z - 1);
                  if (pixel != adjPixel) {
                     faces = (byte)(faces | 16);
                  }

                  adjPixel = this.getTexelWrapped(x, z + 1);
                  if (pixel != adjPixel) {
                     faces = (byte)(faces | 32);
                  }

                  renderFaces[idx] = faces;
               }
            }
         }

         return renderFaces;
      }

      int getTexelWrapped(int x, int z) {
         if (x < 0) {
            x = this.width - 1;
         }

         if (x > this.width - 1) {
            x = 0;
         }

         if (z < 0) {
            z = this.width - 1;
         }

         if (z > this.width - 1) {
            z = 0;
         }

         return this.pixels[this.getIdx(x, z)];
      }

      int getWrappedIdx(int x, int z) {
         x = Math.floorMod(x, this.width);
         z = Math.floorMod(z, this.width);
         return this.getIdx(x, z);
      }

      int getIdx(int x, int z) {
         return z * this.width + x;
      }

      private static boolean hasColor(int pixel) {
         return (pixel >> 24 & 0xFF) > 1;
      }
   }
}
