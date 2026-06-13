package com.xeno.vulkan.util;

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
import org.lwjgl.system.MemoryUtil;

public class MappedBuffer {
   public final ByteBuffer buffer;
   public final long ptr;

   public static MappedBuffer createFromBuffer(ByteBuffer buffer) {
      return new MappedBuffer(buffer, MemoryUtil.memAddress0(buffer));
   }

   MappedBuffer(ByteBuffer buffer, long ptr) {
      this.buffer = buffer;
      this.ptr = ptr;
   }

   public MappedBuffer(int size) {
      this.buffer = MemoryUtil.memAlloc(size);
      this.ptr = MemoryUtil.memAddress0(this.buffer);
   }

   public void putFloat(int idx, float f) {
      MemoryAccess.memPutFloat(this.ptr + idx, f);
   }

   public void putInt(int idx, int f) {
      MemoryAccess.memPutInt(this.ptr + idx, f);
   }

   public float getFloat(int idx) {
      return MemoryAccess.memGetFloat(this.ptr + idx);
   }

   public int getInt(int idx) {
      return MemoryAccess.memGetInt(this.ptr + idx);
   }
}
