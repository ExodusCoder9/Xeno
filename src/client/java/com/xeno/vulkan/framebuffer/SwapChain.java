package com.xeno.vulkan.framebuffer;

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


import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import com.xeno.Initializer;
import com.xeno.render.util.MathUtil;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.queue.Queue;
import com.xeno.vulkan.texture.SamplerManager;
import com.xeno.vulkan.texture.VulkanImage;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR.Buffer;

public class SwapChain extends Framebuffer {
   private static final int defUncappedMode = checkPresentMode(0, 1);
   private final Long2ReferenceOpenHashMap<long[]> FBO_map = new Long2ReferenceOpenHashMap();
   private long swapChainId = 0L;
   private List<VulkanImage> swapChainImages;
   private VkExtent2D extent2D;
   public boolean isBGRAformat;
   private boolean vsync = false;
   private boolean hasImages = false;
   private boolean acquired = false;

   public SwapChain() {
      this.attachmentCount = 2;
      this.depthFormat = Vulkan.getDefaultDepthFormat();
      this.hasColorAttachment = true;
      this.hasDepthAttachment = true;
      this.recreate();
   }

   public void recreate() {
      if (this.depthAttachment != null) {
         this.depthAttachment.free();
         this.depthAttachment = null;
      }

      this.createSwapChain();
   }

   private void createSwapChain() {
      MemoryStack stack = MemoryStack.stackPush();

      label69: {
         try {
            VkDevice device = Vulkan.getVkDevice();
            DeviceManager.SurfaceProperties surfaceProperties = DeviceManager.querySurfaceProperties(device.getPhysicalDevice(), stack);
            VkSurfaceFormatKHR surfaceFormat = this.getFormat(surfaceProperties.formats);
            int presentMode = this.getPresentMode(surfaceProperties.presentModes);
            VkExtent2D extent = getExtent(surfaceProperties.capabilities);
            if (extent.width() == 0 && extent.height() == 0) {
               if (this.swapChainId != 0L) {
                  this.swapChainImages.forEach(imagex -> VK10.vkDestroyImageView(device, imagex.getImageView(), null));
                  KHRSwapchain.vkDestroySwapchainKHR(device, this.swapChainId, null);
                  this.swapChainId = 0L;
               }

               this.width = 0;
               this.height = 0;
               this.hasImages = false;
               this.acquired = false;
               break label69;
            }

            int requestedImages = surfaceProperties.capabilities.minImageCount() + 1;
            if (surfaceProperties.capabilities.maxImageCount() > 0 && requestedImages > surfaceProperties.capabilities.maxImageCount()) {
               requestedImages = surfaceProperties.capabilities.maxImageCount();
            }

            IntBuffer imageCount = stack.ints(requestedImages);
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);
            createInfo.sType(1000001000);
            createInfo.surface(Vulkan.getSurface());
            this.format = surfaceFormat.format();
            this.extent2D = VkExtent2D.create().set(extent);
            createInfo.minImageCount(requestedImages);
            createInfo.imageFormat(this.format);
            createInfo.imageColorSpace(surfaceFormat.colorSpace());
            createInfo.imageExtent(extent);
            createInfo.imageArrayLayers(1);
            createInfo.imageUsage(21);
            Queue.QueueFamilyIndices indices = Queue.getQueueFamilies();
            if (indices.graphicsFamily != indices.presentFamily) {
               createInfo.imageSharingMode(1);
               createInfo.pQueueFamilyIndices(stack.ints(indices.graphicsFamily, indices.presentFamily));
            } else {
               createInfo.imageSharingMode(0);
            }

            createInfo.preTransform(surfaceProperties.capabilities.currentTransform());
            createInfo.compositeAlpha(1);
            createInfo.presentMode(presentMode);
            createInfo.clipped(true);
            long oldSwapchain = this.swapChainId;
            if (oldSwapchain != 0L) {
               createInfo.oldSwapchain(oldSwapchain);
            }

            LongBuffer pSwapChain = stack.longs(0L);
            int result = KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, null, pSwapChain);
            Vulkan.checkResult(result, "Failed to create swap chain");
            this.swapChainId = pSwapChain.get(0);
            if (oldSwapchain != 0L) {
               this.swapChainImages.forEach(imagex -> VK10.vkDestroyImageView(device, imagex.getImageView(), null));
               this.swapChainImages.clear();
               KHRSwapchain.vkDestroySwapchainKHR(device, oldSwapchain, null);
            }
            KHRSwapchain.vkGetSwapchainImagesKHR(device, this.swapChainId, imageCount, null);
            LongBuffer pSwapchainImages = stack.mallocLong(imageCount.get(0));
            KHRSwapchain.vkGetSwapchainImagesKHR(device, this.swapChainId, imageCount, pSwapchainImages);
            this.swapChainImages = new ArrayList<>(imageCount.get(0));
            this.hasImages = true;
            this.width = this.extent2D.width();
            this.height = this.extent2D.height();

            for (int i = 0; i < pSwapchainImages.capacity(); i++) {
               long imageId = pSwapchainImages.get(i);
               long imageView = VulkanImage.createImageView(imageId, this.format, 1, 1, 1);
               VulkanImage image = new VulkanImage("Swapchain", imageId, this.format, 1, this.width, this.height, 4, 0, imageView);
               long samplerId = SamplerManager.getSampler(true, true, 0);
               image.setSampler(samplerId);
               this.swapChainImages.add(image);
            }
         } catch (Throwable var23) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var22) {
                  var23.addSuppressed(var22);
               }
            }

            throw var23;
         }

         if (stack != null) {
            stack.close();
         }

         this.createDepthResources();
         return;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private long[] createFramebuffers(RenderPass renderPass) {
      MemoryStack stack = MemoryStack.stackPush();

      long[] var10;
      try {
         long[] framebuffers = new long[this.swapChainImages.size()];

         for (int i = 0; i < this.swapChainImages.size(); i++) {
            LongBuffer attachments = stack.longs(this.swapChainImages.get(i).getImageView(), this.depthAttachment.getImageView());
            LongBuffer pFramebuffer = stack.mallocLong(1);
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
            framebufferInfo.sType(37);
            framebufferInfo.renderPass(renderPass.getId());
            framebufferInfo.width(this.width);
            framebufferInfo.height(this.height);
            framebufferInfo.layers(1);
            framebufferInfo.pAttachments(attachments);
            if (VK10.vkCreateFramebuffer(Vulkan.getVkDevice(), framebufferInfo, null, pFramebuffer) != 0) {
               throw new RuntimeException("Failed to create framebuffer");
            }

            framebuffers[i] = pFramebuffer.get(0);
         }

         var10 = framebuffers;
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
      }

      return var10;
   }

   private void createDepthResources() {
      this.depthAttachment = VulkanImage.createDepthImage(this.depthFormat, this.width, this.height, 36, false, false);
   }

   @Override
   protected long getFramebufferId(RenderPass renderPass) {
      long[] framebuffers = (long[])this.FBO_map.computeIfAbsent(renderPass.id, renderPass1 -> this.createFramebuffers(renderPass));
      return framebuffers[Renderer.getCurrentImage()];
   }

   @Override
   public void cleanUp() {
      VkDevice device = Vulkan.getVkDevice();
      KHRSwapchain.vkDestroySwapchainKHR(device, this.swapChainId, null);
      this.swapChainImages.forEach(image -> VK10.vkDestroyImageView(device, image.getImageView(), null));
      this.depthAttachment.free();
   }

   private VkSurfaceFormatKHR getFormat(Buffer availableFormats) {
      List<VkSurfaceFormatKHR> list = availableFormats.stream().toList();
      VkSurfaceFormatKHR format = list.get(0);

      for (VkSurfaceFormatKHR availableFormat : list) {
         if (availableFormat.format() == 37 && availableFormat.colorSpace() == 0) {
            return availableFormat;
         }

         if (availableFormat.format() == 44 && availableFormat.colorSpace() == 0) {
            format = availableFormat;
         }
      }

      if (format.format() == 44) {
         this.isBGRAformat = true;
      }

      return format;
   }

   private int getPresentMode(IntBuffer availablePresentModes) {
      int requestedMode = this.vsync ? 2 : defUncappedMode;
      if (requestedMode == 2) {
         return 2;
      }

      for (int i = 0; i < availablePresentModes.capacity(); i++) {
         if (availablePresentModes.get(i) == requestedMode) {
            return requestedMode;
         }
      }

      Initializer.LOGGER.warn("Requested mode not supported: " + this.getDisplayModeString(requestedMode) + ": using FIFO present mode");
      return 2;
   }

   private String getDisplayModeString(int requestedMode) {
      return switch (requestedMode) {
         case 0 -> "Immediate";
         case 1 -> "Mailbox (FastSync)";
         default -> "FIFO (VSync)";
         case 3 -> "FIFO Relaxed (Adaptive VSync)";
      };
   }

   private static VkExtent2D getExtent(VkSurfaceCapabilitiesKHR capabilities) {
      if (capabilities.currentExtent().width() != -1) {
         return capabilities.currentExtent();
      }

      IntBuffer width = MemoryStack.stackGet().ints(0);
      IntBuffer height = MemoryStack.stackGet().ints(0);
      GLFW.glfwGetFramebufferSize(Vulkan.window, width, height);
      VkExtent2D actualExtent = VkExtent2D.malloc(MemoryStack.stackPush()).set(width.get(0), height.get(0));
      VkExtent2D minExtent = capabilities.minImageExtent();
      VkExtent2D maxExtent = capabilities.maxImageExtent();
      actualExtent.width(MathUtil.clamp(minExtent.width(), maxExtent.width(), actualExtent.width()));
      actualExtent.height(MathUtil.clamp(minExtent.height(), maxExtent.height(), actualExtent.height()));
      return actualExtent;
   }

   private static int checkPresentMode(int... requestedModes) {
      MemoryStack stack = MemoryStack.stackPush();

      int var8;
      label59: {
         byte var11;
         try {
            IntBuffer a = DeviceManager.querySurfaceProperties(DeviceManager.vkDevice.getPhysicalDevice(), stack).presentModes;

            for (int dMode : requestedModes) {
               for (int i = 0; i < a.capacity(); i++) {
                  if (a.get(i) == dMode) {
                     var8 = dMode;
                     break label59;
                  }
               }
            }

            var11 = 2;
         } catch (Throwable var10) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (stack != null) {
            stack.close();
         }

         return var11;
      }

      if (stack != null) {
         stack.close();
      }

      return var8;
   }

   public long getId() {
      return this.swapChainId;
   }

   public List<VulkanImage> getImages() {
      return this.swapChainImages;
   }

   public long getImageId(int i) {
      return this.swapChainImages.get(i).getId();
   }

   public VkExtent2D getExtent() {
      return this.extent2D;
   }

   @Override
   public VulkanImage getColorAttachment() {
      return this.swapChainImages.get(Renderer.getCurrentImage());
   }

   @Override
   public long getColorAttachmentView() {
      return this.getColorAttachment().getImageView();
   }

   public boolean hasImages() {
      return this.hasImages;
   }

   public boolean isAcquired() {
      return this.acquired;
   }

   public void setAcquired(boolean acquired) {
      this.acquired = acquired;
   }

   public boolean isVsync() {
      return this.vsync;
   }

   public void setVsync(boolean vsync) {
      this.vsync = vsync;
   }

   public int getImagesNum() {
      return this.swapChainImages.size();
   }
}
