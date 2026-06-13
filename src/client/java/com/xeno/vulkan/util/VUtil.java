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
package com.xeno.vulkan.util;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collection;
import com.xeno.vulkan.memory.buffer.Buffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

public class VUtil {
   public static final boolean CHECKS = true;
   public static final int UINT32_MAX = -1;
   public static final long UINT64_MAX = -1L;
   public static final Unsafe UNSAFE;

   public static PointerBuffer asPointerBuffer(Collection<String> collection) {
      MemoryStack stack = MemoryStack.stackGet();
      PointerBuffer buffer = stack.mallocPointer(collection.size());
      collection.stream().map(stack::UTF8).forEach(buffer::put);
      return (PointerBuffer)buffer.rewind();
   }

   public static void memcpy(ByteBuffer src, long dstPtr) {
      MemoryUtil.memCopy(MemoryUtil.memAddress0(src), dstPtr, src.capacity());
   }

   public static void memcpy(ByteBuffer src, Buffer dst, long size) {
      if (size > dst.getBufferSize() - dst.getUsedBytes()) {
         throw new IllegalArgumentException("Upload size is greater than available dst buffer size");
      }

      long srcPtr = MemoryUtil.memAddress(src);
      long dstPtr = dst.getDataPtr() + dst.getUsedBytes();
      MemoryUtil.memCopy(srcPtr, dstPtr, size);
   }

   public static void memcpy(Buffer src, ByteBuffer dst, long size) {
      if (size > dst.remaining()) {
         throw new IllegalArgumentException("Upload size is greater than available dst buffer size");
      }

      long srcPtr = src.getDataPtr();
      long dstPtr = MemoryUtil.memAddress(dst);
      MemoryUtil.memCopy(srcPtr, dstPtr, size);
   }

   public static void memcpy(ByteBuffer src, Buffer dst, long size, long srcOffset, long dstOffset) {
      if (size > dst.getBufferSize() - dstOffset) {
         throw new IllegalArgumentException("Upload size is greater than available dst buffer size");
      }

      long dstPtr = dst.getDataPtr() + dstOffset;
      long srcPtr = MemoryUtil.memAddress(src) + srcOffset;
      MemoryUtil.memCopy(srcPtr, dstPtr, size);
   }

   public static int align(int x, int align) {
      int r = x % align;
      return r == 0 ? x : x + align - r;
   }

   static {
      Field f = null;

      try {
         f = Unsafe.class.getDeclaredField("theUnsafe");
         f.setAccessible(true);
         UNSAFE = (Unsafe)f.get(null);
      } catch (NoSuchFieldException | IllegalAccessException e) {
         throw new RuntimeException(e);
      }
   }
}
