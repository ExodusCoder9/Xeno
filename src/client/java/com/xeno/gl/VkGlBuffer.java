package com.xeno.gl;

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


import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.system.MemoryUtil;

public class VkGlBuffer {
   private static int ID_COUNTER = 1;
   private static final Int2ReferenceOpenHashMap<VkGlBuffer> map = new Int2ReferenceOpenHashMap();
   private static int boundId = 0;
   private static VkGlBuffer boundBuffer;
   private static VkGlBuffer arrayBufferBound;
   private static VkGlBuffer pixelPackBufferBound;
   private static VkGlBuffer pixelUnpackBufferBound;
   int id;
   int target;
   ByteBuffer data;

   public static int glGenBuffers() {
      int id = ID_COUNTER;
      map.put(id, new VkGlBuffer(id));
      ID_COUNTER++;
      return id;
   }

   public static void glBindBuffer(int target, int buffer) {
      boundId = buffer;
      VkGlBuffer glBuffer = (VkGlBuffer)map.get(buffer);
      if (buffer > 0 && glBuffer == null) {
         throw new NullPointerException("bound texture is null");
      }

      if (glBuffer != null) {
         glBuffer.target = target;
      }

      switch (target) {
         case 34962:
            arrayBufferBound = glBuffer;
            break;
         case 35051:
            pixelPackBufferBound = glBuffer;
            break;
         case 35052:
            pixelUnpackBufferBound = glBuffer;
            break;
         default:
            throw new IllegalStateException("Unexpected value: " + target);
      }
   }

   public static void glBufferData(int target, ByteBuffer byteBuffer, int usage) {
      checkTarget(target);
      pixelUnpackBufferBound = boundBuffer;
   }

   public static void glBufferData(int target, long size, int usage) {
      VkGlBuffer buffer = switch (target) {
         case 35051 -> pixelPackBufferBound;
         case 35052 -> pixelUnpackBufferBound;
         default -> throw new IllegalStateException("Unexpected value: " + target);
      };
      buffer.allocate((int)size);
   }

   public static ByteBuffer glMapBuffer(int target, int access) {
      VkGlBuffer buffer = switch (target) {
         case 35051 -> pixelPackBufferBound;
         case 35052 -> pixelUnpackBufferBound;
         default -> throw new IllegalStateException("Unexpected value: " + target);
      };
      ByteBuffer mappedBuffer = buffer.data;
      mappedBuffer.position(0);
      return mappedBuffer;
   }

   public static boolean glUnmapBuffer(int i) {
      return true;
   }

   public static void glDeleteBuffers(IntBuffer intBuffer) {
      for (int i = intBuffer.position(); i < intBuffer.limit(); i++) {
         glDeleteBuffers(intBuffer.get(i));
      }
   }

   public static void glDeleteBuffers(int id) {
      VkGlBuffer buffer = (VkGlBuffer)map.remove(id);
      if (buffer != null) {
         buffer.freeData();
      }
   }

   public static VkGlBuffer getPixelUnpackBufferBound() {
      return pixelUnpackBufferBound;
   }

   public static VkGlBuffer getPixelPackBufferBound() {
      return pixelPackBufferBound;
   }

   private static void checkTarget(int target) {
      if (target != 35052 && target != 35051) {
         throw new IllegalArgumentException("target %d not supported".formatted(target));
      }
   }

   public VkGlBuffer(int id) {
      this.id = id;
   }

   private void allocate(int size) {
      if (this.data != null) {
         this.freeData();
      }

      this.data = MemoryUtil.memAlloc(size);
   }

   private ByteBuffer getData() {
      return this.data;
   }

   private void freeData() {
      MemoryUtil.memFree(this.data);
   }
}
