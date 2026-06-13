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
package com.xeno.vulkan.memory;

import java.nio.ByteBuffer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.memory.buffer.StagingBuffer;
import com.xeno.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

public class MemoryTypes {
   public static MemoryType GPU_MEM;
   public static MemoryType HOST_MEM;

   public static void createMemoryTypes() {
      for (int i = 0; i < DeviceManager.memoryProperties.memoryTypeCount(); i++) {
         VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(i);
         VkMemoryHeap heap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());
         int propertyFlags = memoryType.propertyFlags();
         if (propertyFlags == 1) {
            GPU_MEM = new MemoryTypes.DeviceLocalMemory(memoryType, heap);
         }

         if (propertyFlags == 6) {
            HOST_MEM = new MemoryTypes.HostCoherentMemory(memoryType, heap);
         }
      }

      if (GPU_MEM == null || HOST_MEM == null) {
         for (int i = 0; i < DeviceManager.memoryProperties.memoryTypeCount(); i++) {
            VkMemoryType memoryType = DeviceManager.memoryProperties.memoryTypes(i);
            VkMemoryHeap heap = DeviceManager.memoryProperties.memoryHeaps(memoryType.heapIndex());
            if ((memoryType.propertyFlags() & 3) == 3) {
               GPU_MEM = new MemoryTypes.DeviceMappableMemory(memoryType, heap);
            }

            if ((memoryType.propertyFlags() & 6) == 6) {
               HOST_MEM = new MemoryTypes.HostLocalFallbackMemory(memoryType, heap);
            }

            if (GPU_MEM != null && HOST_MEM != null) {
               return;
            }
         }

         GPU_MEM = HOST_MEM;
      }
   }

   public static class DeviceLocalMemory extends MemoryType {
      DeviceLocalMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
         super(MemoryType.Type.DEVICE_LOCAL, vkMemoryType, vkMemoryHeap);
      }

      @Override
      public void createBuffer(Buffer buffer, long size) {
         MemoryManager.getInstance().createBuffer(buffer, size, 3 | buffer.usage, 1);
      }

      @Override
      public void copyToBuffer(Buffer buffer, ByteBuffer src, long size, long srcOffset, long dstOffset) {
         StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
         stagingBuffer.copyBuffer((int)size, src);
         DeviceManager.getTransferQueue().copyBufferCmd(stagingBuffer.getId(), stagingBuffer.getOffset(), buffer.getId(), dstOffset, size);
      }

      @Override
      public void copyFromBuffer(Buffer buffer, long bufferSize, ByteBuffer byteBuffer) {
      }

      public long copyBuffer(Buffer src, Buffer dst) {
         if (dst.getBufferSize() < src.getBufferSize()) {
            throw new IllegalArgumentException("dst size is less than src size.");
         } else {
            return DeviceManager.getTransferQueue().copyBufferCmd(src.getId(), 0L, dst.getId(), 0L, src.getBufferSize());
         }
      }

      @Override
      public boolean mappable() {
         return false;
      }
   }

   static class DeviceMappableMemory extends MemoryTypes.MappableMemory {
      DeviceMappableMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
         super(MemoryType.Type.DEVICE_LOCAL, vkMemoryType, vkMemoryHeap);
      }

      @Override
      public void createBuffer(Buffer buffer, long size) {
         MemoryManager.getInstance().createBuffer(buffer, size, 3 | buffer.usage, 3);
      }
   }

   static class HostCoherentMemory extends MemoryTypes.MappableMemory {
      HostCoherentMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
         super(MemoryType.Type.HOST_LOCAL, vkMemoryType, vkMemoryHeap);
      }

      @Override
      public void createBuffer(Buffer buffer, long size) {
         MemoryManager.getInstance().createBuffer(buffer, size, 3 | buffer.usage, 6);
      }
   }

   static class HostLocalFallbackMemory extends MemoryTypes.MappableMemory {
      HostLocalFallbackMemory(VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
         super(MemoryType.Type.HOST_LOCAL, vkMemoryType, vkMemoryHeap);
      }

      @Override
      public void createBuffer(Buffer buffer, long size) {
         MemoryManager.getInstance().createBuffer(buffer, size, 3 | buffer.usage, 6);
      }
   }

   abstract static class MappableMemory extends MemoryType {
      MappableMemory(MemoryType.Type type, VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
         super(type, vkMemoryType, vkMemoryHeap);
      }

      @Override
      public void copyToBuffer(Buffer buffer, ByteBuffer src, long size, long srcOffset, long dstOffset) {
         VUtil.memcpy(src, buffer, size, srcOffset, dstOffset);
      }

      @Override
      public void copyFromBuffer(Buffer buffer, long size, ByteBuffer byteBuffer) {
         MemoryUtil.memCopy(buffer.getDataPtr(), MemoryUtil.memAddress(byteBuffer), size);
         VUtil.memcpy(buffer, byteBuffer, size);
      }

      @Override
      public boolean mappable() {
         return true;
      }
   }
}
