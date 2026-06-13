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


import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.memory.MemoryType;
import com.xeno.vulkan.util.VUtil;

public class UniformBuffer extends Buffer {
   private static final int MIN_OFFSET_ALIGNMENT = (int)DeviceManager.deviceProperties.limits().minUniformBufferOffsetAlignment();

   public static int getAlignedSize(int uploadSize) {
      return VUtil.align(uploadSize, MIN_OFFSET_ALIGNMENT);
   }

   public UniformBuffer(int size, MemoryType memoryType) {
      super("Uniform buffer", 16, memoryType);
      this.createBuffer(size);
   }

   public void checkCapacity(int size) {
      if (size > this.bufferSize - this.usedBytes) {
         this.resizeBuffer((this.bufferSize + size) * 2L);
      }
   }

   public void updateOffset(int alignedSize) {
      this.usedBytes += alignedSize;
   }

   public long getPointer() {
      return this.dataPtr + this.usedBytes;
   }
}
