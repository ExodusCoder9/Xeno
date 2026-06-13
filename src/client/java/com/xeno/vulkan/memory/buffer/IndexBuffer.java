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


import com.xeno.vulkan.memory.MemoryType;

public class IndexBuffer extends Buffer {
   public IndexBuffer.IndexType indexType;

   public IndexBuffer(int size, MemoryType type) {
      this(size, type, IndexBuffer.IndexType.UINT16);
   }

   public IndexBuffer(int size, MemoryType type, IndexBuffer.IndexType indexType) {
      super("Index buffer", 64, type);
      this.indexType = indexType;
      this.createBuffer(size);
   }

   public enum IndexType {
      UINT16(2, 0),
      UINT32(4, 1);

      public final int size;
      public final int value;

      IndexType(int size, int value) {
         this.size = size;
         this.value = value;
      }
   }
}
