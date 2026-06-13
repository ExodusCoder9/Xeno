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
import com.xeno.vulkan.memory.buffer.Buffer;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;

public abstract class MemoryType {
   final MemoryType.Type type;
   public final VkMemoryType vkMemoryType;
   public final VkMemoryHeap vkMemoryHeap;

   MemoryType(MemoryType.Type type, VkMemoryType vkMemoryType, VkMemoryHeap vkMemoryHeap) {
      this.type = type;
      this.vkMemoryType = vkMemoryType;
      this.vkMemoryHeap = vkMemoryHeap;
   }

   public abstract void createBuffer(Buffer var1, long var2);

   public abstract void copyToBuffer(Buffer var1, ByteBuffer var2, long var3, long var5, long var7);

   public abstract void copyFromBuffer(Buffer var1, long var2, ByteBuffer var4);

   public abstract boolean mappable();

   public MemoryType.Type getType() {
      return this.type;
   }

   public enum Type {
      DEVICE_LOCAL,
      HOST_LOCAL;
   }
}
