package com.xeno.vulkan.memory.buffer;

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


import java.nio.ByteBuffer;
import com.xeno.vulkan.Synchronization;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.memory.MemoryType;
import com.xeno.vulkan.queue.CommandPool;
import com.xeno.vulkan.queue.TransferQueue;

public class IndirectBuffer extends Buffer {
   CommandPool.CommandBuffer commandBuffer;

   public IndirectBuffer(int size, MemoryType type) {
      super("Indirect buffer", 256, type);
      this.createBuffer(size);
   }

   public void recordCopyCmd(ByteBuffer byteBuffer) {
      int size = byteBuffer.remaining();
      if (size > this.bufferSize - this.usedBytes) {
         this.resizeBuffer((long)((float)this.bufferSize * 1.5F));
         this.usedBytes = 0L;
      }

      if (this.type.mappable()) {
         this.type.copyToBuffer(this, byteBuffer, size, 0L, this.usedBytes);
      } else {
         if (this.commandBuffer == null) {
            this.commandBuffer = DeviceManager.getTransferQueue().beginCommands();
         }

         StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
         stagingBuffer.copyBuffer(size, byteBuffer);
         TransferQueue.uploadBufferCmd(this.commandBuffer.getHandle(), stagingBuffer.id, stagingBuffer.offset, this.getId(), this.getUsedBytes(), size);
      }

      this.offset = this.usedBytes;
      this.usedBytes += size;
   }

   public void submitUploads() {
      if (this.commandBuffer != null) {
         DeviceManager.getTransferQueue().submitCommands(this.commandBuffer);
         Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);
         this.commandBuffer = null;
      }
   }
}
