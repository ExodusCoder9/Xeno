package com.xeno.vulkan.pass;

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


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.List;
import java.util.function.IntSupplier;
import com.xeno.render.engine.VkGpuDevice;
import com.xeno.render.engine.VkGpuTexture;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.framebuffer.Framebuffer;
import com.xeno.vulkan.framebuffer.RenderPass;
import com.xeno.vulkan.framebuffer.SwapChain;
import com.xeno.vulkan.texture.VTextureSelector;
import com.xeno.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D.Buffer;

public class DefaultMainPass implements MainPass {
   private Framebuffer mainFramebuffer;
   private RenderPass mainRenderPass;
   private RenderPass auxRenderPass;
   private GpuTexture[] colorAttachmentTextures;
   private GpuTextureView[] colorAttachmentTextureViews;
   IntSupplier imageIdxSupplier;
   private GpuTexture depthAttachmentTexture;

   public static DefaultMainPass create() {
      return new DefaultMainPass();
   }

   DefaultMainPass() {
      this.createResources();
   }

   private void createResources() {
      if (this.mainFramebuffer != null) {
         if (this.mainFramebuffer != Renderer.getInstance().getSwapChain()) {
            this.mainFramebuffer.cleanUp(true);
         }

         this.mainRenderPass.cleanUp();
         this.auxRenderPass.cleanUp();
      }

      Framebuffer framebuffer;
      if (Renderer.getInstance().getSwapChain().hasImages()) {
         framebuffer = Renderer.getInstance().getSwapChain();
      } else {
         framebuffer = Framebuffer.builder(10, 10, 1, true).build();
      }

      this.mainFramebuffer = framebuffer;
      this.createRenderPasses();
      this.createAttachmentTextures();
   }

   private void createRenderPasses() {
      RenderPass.Builder builder = RenderPass.builder(this.mainFramebuffer);
      builder.getColorAttachmentInfo().setFinalLayout(2);
      builder.getColorAttachmentInfo().setOps(2, 0);
      builder.getDepthAttachmentInfo().setOps(2, 0);
      this.mainRenderPass = builder.build();
      builder = RenderPass.builder(this.mainFramebuffer);
      builder.getColorAttachmentInfo().setOps(0, 0);
      builder.getDepthAttachmentInfo().setOps(0, 0);
      builder.getColorAttachmentInfo().setFinalLayout(2);
      this.auxRenderPass = builder.build();
   }

   @Override
   public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
      Framebuffer framebuffer = this.mainFramebuffer;
      VulkanImage colorAttachment = framebuffer.getColorAttachment();
      if (colorAttachment.getCurrentLayout() != 2) {
         colorAttachment.transitionImageLayout(stack, commandBuffer, 2);
      }
      Renderer.getInstance().beginRenderPass(this.mainRenderPass, framebuffer);
      Renderer.setViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight(), stack);
      Buffer pScissor = framebuffer.scissor(stack);
      VK10.vkCmdSetScissor(commandBuffer, 0, pScissor);
   }

   @Override
   public void end(VkCommandBuffer commandBuffer) {
      Renderer.getInstance().endRenderPass(commandBuffer);
      if (this.mainFramebuffer == Renderer.getInstance().getSwapChain()) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            this.mainFramebuffer.getColorAttachment().transitionImageLayout(stack, commandBuffer, 1000001002);
         } catch (Throwable var6) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (stack != null) {
            stack.close();
         }
      }

      int result = VK10.vkEndCommandBuffer(commandBuffer);
      if (result != 0) {
         throw new RuntimeException("Failed to record command buffer:" + result);
      }
   }

   @Override
   public void cleanUp() {
      this.mainRenderPass.cleanUp();
      this.auxRenderPass.cleanUp();
   }

   @Override
   public void onResize() {
      this.createResources();
   }

   @Override
   public void rebindMainTarget() {
      VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
      RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
      if (boundRenderPass != this.mainRenderPass && boundRenderPass != this.auxRenderPass) {
         Renderer.getInstance().endRenderPass(commandBuffer);
         Renderer.getInstance().beginRenderPass(this.auxRenderPass, this.mainFramebuffer);
      }
   }

   @Override
   public void bindAsTexture() {
      VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
      RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
      if (boundRenderPass == this.mainRenderPass || boundRenderPass == this.auxRenderPass) {
         Renderer.getInstance().endRenderPass(commandBuffer);
      }

      MemoryStack stack = MemoryStack.stackPush();

      try {
         this.mainFramebuffer.getColorAttachment().transitionImageLayout(stack, commandBuffer, 5);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }

      VTextureSelector.bindTexture(this.mainFramebuffer.getColorAttachment());
   }

   @Override
   public Framebuffer getMainFramebuffer() {
      return this.mainFramebuffer;
   }

   @Override
   public GpuTexture getColorAttachment() {
      return this.colorAttachmentTextures[this.imageIdxSupplier.getAsInt()];
   }

   @Override
   public GpuTextureView getColorAttachmentView() {
      return this.colorAttachmentTextureViews[this.imageIdxSupplier.getAsInt()];
   }

   @Override
   public GpuTexture getDepthAttachment() {
      return this.depthAttachmentTexture;
   }

   private void createAttachmentTextures() {
      VkGpuDevice device = (VkGpuDevice)RenderSystem.getDevice().backend;
      SwapChain swapChain = Renderer.getInstance().getSwapChain();
      if (this.mainFramebuffer == swapChain) {
         List<VulkanImage> swapChainImages = swapChain.getImages();
         int imageCount = swapChainImages.size();
         this.colorAttachmentTextures = new GpuTexture[imageCount];
         this.colorAttachmentTextureViews = new GpuTextureView[imageCount];

         for (int i = 0; i < imageCount; i++) {
            VkGpuTexture attachmentTexture = device.gpuTextureFromVulkanImage(swapChainImages.get(i));
            GpuTextureView attachmentTextureView = device.createTextureView(attachmentTexture);
            this.colorAttachmentTextures[i] = attachmentTexture;
            this.colorAttachmentTextureViews[i] = attachmentTextureView;
         }

         this.imageIdxSupplier = Renderer::getCurrentImage;
      } else {
         this.colorAttachmentTextures = new GpuTexture[1];
         this.colorAttachmentTextureViews = new GpuTextureView[1];
         VkGpuTexture attachmentTexture = device.gpuTextureFromVulkanImage(this.mainFramebuffer.getColorAttachment());
         GpuTextureView attachmentTextureView = device.createTextureView(attachmentTexture);
         this.colorAttachmentTextures[0] = attachmentTexture;
         this.colorAttachmentTextureViews[0] = attachmentTextureView;
         this.imageIdxSupplier = () -> 0;
      }

      this.depthAttachmentTexture = device.gpuTextureFromVulkanImage(this.mainFramebuffer.getDepthAttachment());
   }
}
