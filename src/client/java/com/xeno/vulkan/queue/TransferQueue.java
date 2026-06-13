package com.xeno.vulkan.queue;

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


import com.xeno.vulkan.Synchronization;
import com.xeno.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkBufferCopy.Buffer;

public class TransferQueue extends Queue {
   private static final VkDevice DEVICE = Vulkan.getVkDevice();

   public TransferQueue(MemoryStack stack, int familyIndex) {
      super(stack, familyIndex);
   }

   public long copyBufferCmd(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {
      MemoryStack stack = MemoryStack.stackPush();

      long var14;
      try {
         CommandPool.CommandBuffer commandBuffer = this.beginCommands();
         Buffer copyRegion = VkBufferCopy.calloc(1, stack);
         copyRegion.size(size);
         copyRegion.srcOffset(srcOffset);
         copyRegion.dstOffset(dstOffset);
         VK10.vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);
         this.submitCommands(commandBuffer);
         Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
         var14 = commandBuffer.fence;
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

      return var14;
   }

   public void uploadBufferImmediate(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         CommandPool.CommandBuffer commandBuffer = this.beginCommands();
         Buffer copyRegion = VkBufferCopy.calloc(1, stack);
         copyRegion.size(size);
         copyRegion.srcOffset(srcOffset);
         copyRegion.dstOffset(dstOffset);
         VK10.vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, copyRegion);
         this.submitCommands(commandBuffer);
         VK10.vkWaitForFences(DEVICE, commandBuffer.fence, true, -1L);
         commandBuffer.reset();
      } catch (Throwable var15) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }
         }

         throw var15;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static void uploadBufferCmd(VkCommandBuffer commandBuffer, long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         Buffer copyRegion = VkBufferCopy.calloc(1, stack);
         copyRegion.size(size);
         copyRegion.srcOffset(srcOffset);
         copyRegion.dstOffset(dstOffset);
         VK10.vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);
      } catch (Throwable var15) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }
         }

         throw var15;
      }

      if (stack != null) {
         stack.close();
      }
   }
}
