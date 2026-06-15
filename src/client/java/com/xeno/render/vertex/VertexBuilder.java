package com.xeno.render.vertex;

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


import com.xeno.vulkan.util.MemoryAccess;

public interface VertexBuilder {
   void vertex(long var1, float var3, float var4, float var5, int var6, float var7, float var8, int var9, int var10);

   void position(long var1, float var3, float var4, float var5);

   void color(long var1, int var3);

   void uv(long var1, float var3, float var4);

   void light(long var1, int var3);

   void normal(long var1, int var3);

   int getStride();

   class CompressedVertexBuilder implements VertexBuilder {
      private static final int VERTEX_SIZE = 16;
      public static final float POS_CONV_MUL = 2048.0F;
      public static final float POS_OFFSET = -4.0F;
      public static final float POS_OFFSET_CONV = -8192.0F;
      public static final float UV_CONV_MUL = 32768.0F;

      public CompressedVertexBuilder() {
      }

      @Override
      public void vertex(long ptr, float x, float y, float z, int color, float u, float v, int light, int packedNormal) {
         short sX = (short)(x * 2048.0F + -8192.0F);
         short sY = (short)(y * 2048.0F + -8192.0F);
         short sZ = (short)(z * 2048.0F + -8192.0F);
         short l = (short)(light >>> 8 & 0xFF00 | light & 0xFF);
         short sU = (short)(u * 32768.0F);
         short sV = (short)(v * 32768.0F);

         long firstPart = ((long)sX & 0xFFFFL) | (((long)sY & 0xFFFFL) << 16) | (((long)sZ & 0xFFFFL) << 32) | (((long)l & 0xFFFFL) << 48);
         long secondPart = ((long)sU & 0xFFFFL) | (((long)sV & 0xFFFFL) << 16) | (((long)color & 0xFFFFFFFFL) << 32);

         MemoryAccess.memPutLong(ptr + 0L, firstPart);
         MemoryAccess.memPutLong(ptr + 8L, secondPart);
      }

      @Override
      public void position(long ptr, float x, float y, float z) {
         short sX = (short)(x * 2048.0F + -8192.0F);
         short sY = (short)(y * 2048.0F + -8192.0F);
         short sZ = (short)(z * 2048.0F + -8192.0F);
         MemoryAccess.memPutInt(ptr + 0L, (sY << 16) | (sX & 0xFFFF));
         MemoryAccess.memPutShort(ptr + 4L, sZ);
      }

      @Override
      public void color(long ptr, int color) {
         MemoryAccess.memPutInt(ptr + 12L, color);
      }

      @Override
      public void uv(long ptr, float u, float v) {
         short sU = (short)(u * 32768.0F);
         short sV = (short)(v * 32768.0F);
         MemoryAccess.memPutInt(ptr + 8L, (sV << 16) | (sU & 0xFFFF));
      }

      @Override
      public void light(long ptr, int light) {
         short l = (short)(light >>> 8 & 0xFF00 | light & 0xFF);
         MemoryAccess.memPutShort(ptr + 6L, l);
      }

      @Override
      public void normal(long ptr, int normal) {
      }

      @Override
      public int getStride() {
         return 16;
      }
   }

   class DefaultVertexBuilder implements VertexBuilder {
      private static final int VERTEX_SIZE = 32;

      public DefaultVertexBuilder() {
      }

      @Override
      public void vertex(long ptr, float x, float y, float z, int color, float u, float v, int light, int packedNormal) {
         MemoryAccess.memPutFloat(ptr + 0L, x);
         MemoryAccess.memPutFloat(ptr + 4L, y);
         MemoryAccess.memPutFloat(ptr + 8L, z);
         MemoryAccess.memPutInt(ptr + 12L, color);
         MemoryAccess.memPutFloat(ptr + 16L, u);
         MemoryAccess.memPutFloat(ptr + 20L, v);
         MemoryAccess.memPutInt(ptr + 24L, light);
         MemoryAccess.memPutInt(ptr + 28L, packedNormal);
      }

      @Override
      public void position(long ptr, float x, float y, float z) {
      }

      @Override
      public void color(long ptr, int color) {
      }

      @Override
      public void uv(long ptr, float u, float v) {
      }

      @Override
      public void light(long ptr, int light) {
      }

      @Override
      public void normal(long ptr, int normal) {
      }

      @Override
      public int getStride() {
         return 32;
      }
   }
}
