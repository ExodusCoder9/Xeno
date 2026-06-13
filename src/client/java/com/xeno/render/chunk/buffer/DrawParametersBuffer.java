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
package com.xeno.render.chunk.buffer;

import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.util.MemoryAccess;
import org.lwjgl.system.MemoryUtil;

public abstract class DrawParametersBuffer {
   static final long INDEX_COUNT_OFFSET = 0L;
   static final long FIRST_INDEX_OFFSET = 4L;
   static final long VERTEX_OFFSET_OFFSET = 8L;
   static final long BASE_INSTANCE_OFFSET = 12L;
   public static final long STRIDE = 16L;
   static final int SECTIONS = 512;
   static final int FACINGS = 7;

   public static long allocateBuffer() {
      int size = (int)(512 * TerrainRenderType.VALUES.length * QuadFacing.COUNT * 16L);
      long drawParamsPtr = MemoryUtil.nmemAlignedAlloc(32L, size);

      for (long ptr = drawParamsPtr; ptr < drawParamsPtr + size; ptr += 16L) {
         resetParameters(ptr);
      }

      return drawParamsPtr;
   }

   public static void freeBuffer(long ptr) {
      MemoryUtil.nmemAlignedFree(ptr);
   }

   public static long getParamsPtr(long basePtr, int section, int renderType, int facing) {
      return basePtr + ((renderType * 512 + section) * 7 + facing) * 16L;
   }

   public static void resetParameters(long ptr) {
      setIndexCount(ptr, 0);
      setFirstIndex(ptr, 0);
      setVertexOffset(ptr, -1);
      setBaseInstance(ptr, 0);
   }

   public static void setIndexCount(long ptr, int value) {
      MemoryAccess.memPutInt(ptr + 0L, value);
   }

   public static void setFirstIndex(long ptr, int value) {
      MemoryAccess.memPutInt(ptr + 4L, value);
   }

   public static void setVertexOffset(long ptr, int value) {
      MemoryAccess.memPutInt(ptr + 8L, value);
   }

   public static void setBaseInstance(long ptr, int value) {
      MemoryAccess.memPutInt(ptr + 12L, value);
   }

   public static int getIndexCount(long ptr) {
      return MemoryAccess.memGetInt(ptr + 0L);
   }

   public static int getFirstIndex(long ptr) {
      return MemoryAccess.memGetInt(ptr + 4L);
   }

   public static int getVertexOffset(long ptr) {
      return MemoryAccess.memGetInt(ptr + 8L);
   }

   public static int getBaseInstance(long ptr) {
      return MemoryAccess.memGetInt(ptr + 12L);
   }
}
