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


import java.nio.LongBuffer;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.queue.CommandPool;
import com.xeno.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkOffset3D;
import org.lwjgl.vulkan.VkBufferImageCopy.Buffer;

public abstract class ImageUtil {
   public ImageUtil() {
   }

   public static void copyBufferToImageCmd(
      MemoryStack stack,
      VkCommandBuffer commandBuffer,
      long buffer,
      long image,
      int arrayLayer,
      int mipLevel,
      int width,
      int height,
      int xOffset,
      int yOffset,
      int bufferOffset,
      int bufferRowLenght,
      int bufferImageHeight
   ) {
      Buffer region = VkBufferImageCopy.calloc(1, stack);
      region.bufferOffset(bufferOffset);
      region.bufferRowLength(bufferRowLenght);
      region.bufferImageHeight(bufferImageHeight);
      region.imageSubresource().aspectMask(1);
      region.imageSubresource().mipLevel(mipLevel);
      region.imageSubresource().baseArrayLayer(arrayLayer);
      region.imageSubresource().layerCount(1);
      region.imageOffset().set(xOffset, yOffset, 0);
      region.imageExtent(VkExtent3D.calloc(stack).set(width, height, 1));
      VK10.vkCmdCopyBufferToImage(commandBuffer, buffer, image, 7, region);
   }

   public static void downloadTexture(VulkanImage image, long ptr) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         int prevLayout = image.getCurrentLayout();
         CommandPool.CommandBuffer commandBuffer = DeviceManager.getGraphicsQueue().beginCommands();
         image.transitionImageLayout(stack, commandBuffer.getHandle(), 6);
         long imageSize = (long)image.width * image.height * image.formatSize;
         LongBuffer pStagingBuffer = stack.mallocLong(1);
         PointerBuffer pStagingAllocation = stack.pointers(0L);
         MemoryManager.getInstance().createBuffer(imageSize, 2, 6, pStagingBuffer, pStagingAllocation);
         copyImageToBufferCmd(stack, commandBuffer.getHandle(), pStagingBuffer.get(0), image.getId(), 0, image.width, image.height, 0, 0, 0L, 0, 0);
         image.transitionImageLayout(stack, commandBuffer.getHandle(), prevLayout);
         long fence = DeviceManager.getGraphicsQueue().submitCommands(commandBuffer);
         VK10.vkWaitForFences(DeviceManager.vkDevice, fence, true, -1L);
         MemoryManager.MapAndCopy(pStagingAllocation.get(0), data -> VUtil.memcpy(data.getByteBuffer(0, (int)imageSize), ptr));
         MemoryManager.freeBuffer(pStagingBuffer.get(0), pStagingAllocation.get(0));
      } catch (Throwable var13) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var12) {
               var13.addSuppressed(var12);
            }
         }

         throw var13;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void copyImageToBuffer(
      VulkanImage image,
      com.xeno.vulkan.memory.buffer.Buffer buffer,
      int mipLevel,
      int width,
      int height,
      int xOffset,
      int yOffset,
      long bufferOffset,
      int bufferRowLength,
      int bufferImageHeight
   ) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         int prevLayout = image.getCurrentLayout();
         CommandPool.CommandBuffer commandBuffer = DeviceManager.getGraphicsQueue().beginCommands();
         image.transitionImageLayout(stack, commandBuffer.getHandle(), 6);
         copyImageToBufferCmd(
            stack,
            commandBuffer.getHandle(),
            buffer.getId(),
            image.getId(),
            mipLevel,
            width,
            height,
            xOffset,
            yOffset,
            bufferOffset,
            bufferRowLength,
            bufferImageHeight
         );
         image.transitionImageLayout(stack, commandBuffer.getHandle(), prevLayout);
         long fence = DeviceManager.getGraphicsQueue().submitCommands(commandBuffer);
         VK10.vkWaitForFences(DeviceManager.vkDevice, fence, true, -1L);
      } catch (Throwable var17) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var16) {
               var17.addSuppressed(var16);
            }
         }

         throw var17;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void copyImageToBufferCmd(
      MemoryStack stack,
      VkCommandBuffer commandBuffer,
      long buffer,
      long image,
      int mipLevel,
      int width,
      int height,
      int xOffset,
      int yOffset,
      long bufferOffset,
      int bufferRowLength,
      int bufferImageHeight
   ) {
      Buffer region = VkBufferImageCopy.calloc(1, stack);
      region.bufferOffset(bufferOffset);
      region.bufferRowLength(bufferRowLength);
      region.bufferImageHeight(bufferImageHeight);
      region.imageSubresource().aspectMask(1);
      region.imageSubresource().mipLevel(mipLevel);
      region.imageSubresource().baseArrayLayer(0);
      region.imageSubresource().layerCount(1);
      region.imageOffset().set(xOffset, yOffset, 0);
      region.imageExtent().set(width, height, 1);
      VK10.vkCmdCopyImageToBuffer(commandBuffer, image, 6, buffer, region);
   }

   public static void blitFramebuffer(VulkanImage srcImage, VulkanImage dstImage) {
      blitFramebuffer(srcImage, dstImage, 0);
   }

   public static void blitFramebuffer(VulkanImage srcImage, VulkanImage dstImage, int filtering) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
         Renderer.getInstance().endRenderPass(commandBuffer);
         dstImage.transitionImageLayout(stack, commandBuffer, 7);
         srcImage.transitionImageLayout(stack, commandBuffer, 6);
         org.lwjgl.vulkan.VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
         blit.srcOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
         blit.srcOffsets(1, VkOffset3D.calloc(stack).set(srcImage.width, srcImage.height, 1));
         blit.srcSubresource().aspectMask(srcImage.aspect).mipLevel(0).baseArrayLayer(0).layerCount(1);
         blit.dstOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
         blit.dstOffsets(1, VkOffset3D.calloc(stack).set(dstImage.width, dstImage.height, 1));
         blit.dstSubresource().aspectMask(dstImage.aspect).mipLevel(0).baseArrayLayer(0).layerCount(1);
         VK10.vkCmdBlitImage(commandBuffer, srcImage.getId(), 6, dstImage.getId(), 7, blit, filtering);
         dstImage.transitionImageLayout(stack, commandBuffer, 5);
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
   }

   public static void blitFramebuffer(VulkanImage dstImage, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1) {
   }

   public static void generateMipmaps(VulkanImage image) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         CommandPool.CommandBuffer commandBuffer = DeviceManager.getGraphicsQueue().beginCommands();
         image.transitionImageLayout(stack, commandBuffer.getHandle(), 7);

         for (int level = 1; level < image.mipLevels; level++) {
            int prevLevel = level - 1;
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.sType(45);
            barrier.oldLayout(2);
            barrier.newLayout(1);
            barrier.srcQueueFamilyIndex(-1);
            barrier.dstQueueFamilyIndex(-1);
            barrier.image(image.getId());
            barrier.subresourceRange().baseMipLevel(prevLevel);
            barrier.subresourceRange().levelCount(1);
            barrier.subresourceRange().baseArrayLayer(0);
            barrier.subresourceRange().layerCount(-1);
            barrier.subresourceRange().aspectMask(image.aspect);
            barrier.srcAccessMask(4096);
            barrier.dstAccessMask(2048);
            VK10.vkCmdPipelineBarrier(commandBuffer.getHandle(), 4096, 4096, 0, null, null, barrier);
            prevLevel = level - 1;
            org.lwjgl.vulkan.VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
            blit.srcOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
            blit.srcOffsets(1, VkOffset3D.calloc(stack).set(image.width >> prevLevel, image.height >> prevLevel, 1));
            blit.srcSubresource().aspectMask(1).mipLevel(prevLevel).baseArrayLayer(0).layerCount(1);
            blit.dstOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
            blit.dstOffsets(1, VkOffset3D.calloc(stack).set(image.width >> level, image.height >> level, 1));
            blit.dstSubresource().aspectMask(1).mipLevel(level).baseArrayLayer(0).layerCount(1);
            VK10.vkCmdBlitImage(commandBuffer.getHandle(), image.getId(), 6, image.getId(), 7, blit, 1);
         }

         org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
         barrier.sType(45);
         barrier.oldLayout(1);
         barrier.newLayout(5);
         barrier.srcQueueFamilyIndex(-1);
         barrier.dstQueueFamilyIndex(-1);
         barrier.image(image.getId());
         barrier.subresourceRange().baseMipLevel(0);
         barrier.subresourceRange().levelCount(image.mipLevels - 1);
         barrier.subresourceRange().baseArrayLayer(0);
         barrier.subresourceRange().layerCount(-1);
         barrier.subresourceRange().aspectMask(image.aspect);
         barrier.srcAccessMask(4096);
         barrier.dstAccessMask(32);
         VK10.vkCmdPipelineBarrier(commandBuffer.getHandle(), 4096, 8192, 0, null, null, barrier);
         barrier.oldLayout(2);
         barrier.subresourceRange().baseMipLevel(image.mipLevels - 1);
         barrier.subresourceRange().levelCount(1);
         VK10.vkCmdPipelineBarrier(commandBuffer.getHandle(), 4096, 8192, 0, null, null, barrier);
         image.setCurrentLayout(5);
         long fence = DeviceManager.getGraphicsQueue().submitCommands(commandBuffer);
         VK10.vkWaitForFences(DeviceManager.vkDevice, fence, true, -1L);
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
   }

   public static void imageTransferMemoryBarrier(MemoryStack stack, VkCommandBuffer commandBuffer, VulkanImage image, int baseLevel) {
      org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
      barrier.sType(45);
      barrier.oldLayout(image.getCurrentLayout());
      barrier.newLayout(image.getCurrentLayout());
      barrier.srcQueueFamilyIndex(-1);
      barrier.dstQueueFamilyIndex(-1);
      barrier.image(image.getId());
      barrier.subresourceRange().baseMipLevel(baseLevel);
      barrier.subresourceRange().levelCount(1);
      barrier.subresourceRange().baseArrayLayer(0);
      barrier.subresourceRange().layerCount(-1);
      barrier.subresourceRange().aspectMask(image.aspect);
      barrier.srcAccessMask(65536);
      barrier.dstAccessMask(65536);
      VK10.vkCmdPipelineBarrier(commandBuffer, 4096, 4096, 0, null, null, barrier);
   }
}
