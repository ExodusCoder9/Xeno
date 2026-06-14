package com.xeno.render.chunk.buffer;

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


import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import com.xeno.vulkan.Synchronization;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.memory.buffer.StagingBuffer;
import com.xeno.vulkan.queue.CommandPool;
import com.xeno.vulkan.queue.Queue;
import com.xeno.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

public class UploadManager {
   public static UploadManager INSTANCE;
   Queue queue = DeviceManager.getTransferQueue();
   CommandPool.CommandBuffer commandBuffer;
   LongOpenHashSet dstBuffers = new LongOpenHashSet();

   public UploadManager() {
   }

   public static void createInstance() {
      INSTANCE = new UploadManager();
   }

   public void submitUploads() {
      if (this.commandBuffer != null) {
         this.queue.submitCommands(this.commandBuffer);
         Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);
         this.commandBuffer = null;
         this.dstBuffers.clear();
      }
   }

   public void recordUpload(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer src) {
      StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
      stagingBuffer.copyBuffer((int)bufferSize, src);
      this.beginCommands();
      VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();
      if (!this.dstBuffers.add(buffer.getId())) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(1, stack);
            VkBufferMemoryBarrier bufBarrier = barriers.get(0);
            bufBarrier.sType$Default();
            bufBarrier.buffer(buffer.getId());
            bufBarrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            bufBarrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            bufBarrier.size(-1L);
            VK10.vkCmdPipelineBarrier(commandBuffer, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, barriers, null);
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

         this.dstBuffers.clear();
      }

      TransferQueue.uploadBufferCmd(commandBuffer, stagingBuffer.getId(), stagingBuffer.getOffset(), buffer.getId(), dstOffset, bufferSize);
   }

   public void copyBuffer(Buffer src, Buffer dst) {
      this.copyBuffer(src, 0L, dst, 0L, src.getBufferSize());
   }

   public void copyBuffer(Buffer src, long srcOffset, Buffer dst, long dstOffset, long size) {
      this.beginCommands();
      VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkBufferMemoryBarrier.Buffer bufferMemoryBarriers = VkBufferMemoryBarrier.calloc(1, stack);
         VkBufferMemoryBarrier bufferMemoryBarrier = bufferMemoryBarriers.get(0);
         bufferMemoryBarrier.sType$Default();
         bufferMemoryBarrier.buffer(src.getId());
         bufferMemoryBarrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
         bufferMemoryBarrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_READ_BIT);
         bufferMemoryBarrier.size(-1L);
         VK10.vkCmdPipelineBarrier(commandBuffer, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, bufferMemoryBarriers, null);
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

      this.dstBuffers.add(dst.getId());
      TransferQueue.uploadBufferCmd(commandBuffer, src.getId(), srcOffset, dst.getId(), dstOffset, size);
   }

   public void syncUploads() {
      this.submitUploads();
      Synchronization.INSTANCE.waitFences();
   }

   private void beginCommands() {
      if (this.commandBuffer == null) {
         this.commandBuffer = this.queue.beginCommands();
      }
   }
}
