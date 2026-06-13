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
package com.xeno.mixin.render.target;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.xeno.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MainTarget.class)
public class MainTargetMixin extends RenderTarget {
   public MainTargetMixin(boolean useDepth) {
      super("Main", useDepth);
   }

   @Overwrite
   private void createFrameBuffer(int width, int height) {
      this.width = width;
      this.height = height;
   }

   @Override
   public void createBuffers(int i, int j) {
      RenderSystem.assertOnRenderThread();
      GpuDevice gpuDevice = RenderSystem.getDevice();
      int k = gpuDevice.getMaxTextureSize();
      if (i > 0 && i <= k && j > 0 && j <= k) {
         this.width = i;
         this.height = j;
         if (this.useDepth) {
            this.depthTexture = gpuDevice.createTexture(() -> this.label + " / Depth", 15, TextureFormat.DEPTH32, i, j, 1, 1);
            this.depthTextureView = gpuDevice.createTextureView(this.depthTexture);
         }

         this.colorTexture = gpuDevice.createTexture(() -> this.label + " / Color", 15, TextureFormat.RGBA8, i, j, 1, 1);
         this.colorTextureView = gpuDevice.createTextureView(this.colorTexture);
      } else {
         throw new IllegalArgumentException("Window " + i + "x" + j + " size out of bounds (max. size: " + k + ")");
      }
   }

   @Override
   public GpuTexture getColorTexture() {
      return Renderer.getInstance().getMainPass().getColorAttachment();
   }

   @Override
   public GpuTextureView getColorTextureView() {
      return Renderer.getInstance().getMainPass().getColorAttachmentView();
   }

   @Override
   public GpuTexture getDepthTexture() {
      return Renderer.getInstance().getMainPass().getDepthAttachment();
   }
}
