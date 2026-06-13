package com.xeno.vulkan.texture;

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


import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import com.xeno.Initializer;
import com.xeno.gl.VkGlTexture;
import com.xeno.render.engine.VkGpuTexture;
import com.xeno.render.texture.SpriteUpdateUtil;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.shader.Pipeline;
import com.xeno.vulkan.shader.descriptor.ImageDescriptor;
import org.lwjgl.system.MemoryUtil;

public abstract class VTextureSelector {
   public static final int SIZE = 12;
   private static final VulkanImage[] boundTextures = new VulkanImage[12];
   private static final int[] levels = new int[12];
   private static final VulkanImage whiteTexture = VulkanImage.createWhiteTexture();
   private static int activeTexture = 0;

   public VTextureSelector() {
   }

   public static void bindTexture(VulkanImage texture) {
      boundTextures[0] = texture;
   }

   public static void bindTexture(int i, VulkanImage texture) {
      if (i >= 0 && i < 12) {
         boundTextures[i] = texture;
         levels[i] = -1;
      } else {
         Initializer.LOGGER.error(String.format("On Texture binding: index %d out of range [0, %d]", i, 11));
      }
   }

   public static void bindImage(int i, VulkanImage texture, int level) {
      if (i >= 0 && i <= 7) {
         boundTextures[i] = texture;
         levels[i] = level;
      } else {
         Initializer.LOGGER.error(String.format("On Texture binding: index %d out of range [0, %d]", i, 11));
      }
   }

   public static void uploadSubTexture(
      int mipLevel, int width, int height, int xOffset, int yOffset, int unpackSkipRows, int unpackSkipPixels, int unpackRowLength, ByteBuffer buffer
   ) {
      uploadSubTexture(mipLevel, 0, width, height, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, unpackRowLength, MemoryUtil.memAddress(buffer));
   }

   public static void uploadSubTexture(
      int mipLevel,
      int arrayLayer,
      int width,
      int height,
      int xOffset,
      int yOffset,
      int unpackSkipRows,
      int unpackSkipPixels,
      int unpackRowLength,
      long bufferPtr
   ) {
      VulkanImage texture = boundTextures[activeTexture];
      if (texture == null) {
         throw new NullPointerException("Texture is null at index: " + activeTexture);
      }

      SpriteUpdateUtil.addTransitionedLayout(texture);
      texture.uploadSubTextureAsync(mipLevel, arrayLayer, width, height, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, unpackRowLength, bufferPtr);
   }

   public static int getTextureIdx(String name) {
      return switch (name) {
         case "Sampler0", "DiffuseSampler", "InSampler", "CloudFaces", "Sprite", "CurrentSprite" -> 0;
         case "Sampler1", "BlurSampler", "NextSprite" -> 1;
         case "Sampler2" -> 2;
         case "Sampler3" -> 3;
         case "Sampler4" -> 4;
         case "Sampler5" -> 5;
         case "Sampler6" -> 6;
         case "Sampler7" -> 7;
         default -> -1;
      };
   }

   public static void bindShaderTextures(Pipeline pipeline) {
      for (ImageDescriptor state : pipeline.getImageDescriptors()) {
         GpuTextureView textureView = VRenderSystem.getShaderTexture(state.imageIdx);
         if (textureView != null) {
            VkGpuTexture gpuTexture = (VkGpuTexture)textureView.texture();
            int shaderTexture = gpuTexture.glId();
            VkGlTexture texture = VkGlTexture.getTexture(shaderTexture);
            if (texture != null && texture.getVulkanImage() != null) {
               bindTexture(state.imageIdx, texture.getVulkanImage());
            }
         }
      }
   }

   public static VulkanImage getImage(int i) {
      return boundTextures[i];
   }

   public static void setLightTexture(VulkanImage texture) {
      boundTextures[2] = texture;
   }

   public static void setOverlayTexture(VulkanImage texture) {
      boundTextures[1] = texture;
   }

   public static void setActiveTexture(int activeTexture) {
      if (activeTexture < 0 || activeTexture >= 12) {
         Initializer.LOGGER.error(String.format("On Texture binding: index %d out of range [0, %d]", activeTexture, 11));
      }

      VTextureSelector.activeTexture = activeTexture;
   }

   public static VulkanImage getBoundTexture() {
      return boundTextures[activeTexture];
   }

   public static VulkanImage getBoundTexture(int i) {
      return boundTextures[i];
   }

   public static VulkanImage getWhiteTexture() {
      return whiteTexture;
   }
}
