package com.xeno.vulkan.util;

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


import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.function.Consumer;
import net.minecraft.util.ARGB;
import com.xeno.render.engine.VkGpuTexture;
import com.xeno.vulkan.Renderer;

public abstract class ScreenshotUtil {
   public ScreenshotUtil() {
   }

   public static void takeScreenshot(RenderTarget renderTarget, int mipLevel, Consumer<NativeImage> consumer) {
      int width = renderTarget.width;
      int height = renderTarget.height;
      GpuTexture gpuTexture = renderTarget.getColorTexture();
      if (gpuTexture == null) {
         throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
      }

      Renderer.getInstance().flushCmds();
      int pixelSize = TextureFormat.RGBA8.pixelSize();
      GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(() -> "Screenshot buffer", 9, width * height * pixelSize);
      CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
      RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(gpuTexture, gpuBuffer, 0L, () -> {
         MappedView readView = commandEncoder.mapBuffer(gpuBuffer, true, false);

         try {
            NativeImage nativeImage = new NativeImage(width, height, false);
            VkGpuTexture colorAttachment = (VkGpuTexture)Renderer.getInstance().getMainPass().getColorAttachment();
            boolean isBgraFormat = colorAttachment.getVulkanImage().format == 44;
            int size = mipLevel * mipLevel;

            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  if (mipLevel == 1) {
                     int color = readView.data().getInt((x + y * width) * pixelSize);
                     if (isBgraFormat) {
                        color = ColorUtil.BGRAtoRGBA(color);
                     }

                     nativeImage.setPixelABGR(x, y, color | 0xFF000000);
                  } else {
                     int red = 0;
                     int green = 0;
                     int blue = 0;

                     for (int x1 = 0; x1 < mipLevel; x1++) {
                        for (int y1 = 0; y1 < mipLevel; y1++) {
                           int color = readView.data().getInt((x + x1 + (y + y1) * width) * pixelSize);
                           if (isBgraFormat) {
                              color = ColorUtil.BGRAtoRGBA(color);
                           }

                           red += ARGB.red(color);
                           green += ARGB.green(color);
                           blue += ARGB.blue(color);
                        }
                     }

                     nativeImage.setPixelABGR(x, y, ARGB.color(255, red / size, green / size, blue / size));
                  }
               }
            }

            consumer.accept(nativeImage);
         } catch (Throwable t$) {
            if (readView != null) {
               try {
                  readView.close();
               } catch (Throwable x2) {
                  t$.addSuppressed(x2);
               }
            }

            throw t$;
         }

         if (readView != null) {
            readView.close();
         }

         gpuBuffer.close();
      }, 0);
   }
}
