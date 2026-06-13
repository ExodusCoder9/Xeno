package com.xeno.vulkan.memory.buffer.index;

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
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import com.xeno.Initializer;
import com.xeno.vulkan.memory.MemoryTypes;
import com.xeno.vulkan.memory.buffer.IndexBuffer;
import org.lwjgl.system.MemoryUtil;

public class AutoIndexBuffer {
   public static final int U16_MAX_VERTEX_COUNT = 65536;
   public static final int QUAD_U16_MAX_INDEX_COUNT = 98304;
   int vertexCount;
   AutoIndexBuffer.DrawType drawType;
   IndexBuffer indexBuffer;

   public AutoIndexBuffer(int vertexCount, AutoIndexBuffer.DrawType type) {
      this.drawType = type;
      this.createIndexBuffer(vertexCount);
   }

   private void createIndexBuffer(int vertexCount) {
      this.vertexCount = vertexCount;
      IndexBuffer.IndexType indexType = IndexBuffer.IndexType.UINT16;
      if (vertexCount > 65536 && (this.drawType == AutoIndexBuffer.DrawType.QUADS || this.drawType == AutoIndexBuffer.DrawType.LINES)) {
         indexType = IndexBuffer.IndexType.UINT32;
      }
      ByteBuffer buffer = switch (this.drawType) {
         case QUADS -> {
            if (indexType == IndexBuffer.IndexType.UINT16) {
               yield genQuadIndices(vertexCount);
            } else {
               yield genIntQuadIndices(vertexCount);
            }
         }
         case TRIANGLE_FAN -> genTriangleFanIndices(vertexCount);
         case TRIANGLE_STRIP -> genTriangleStripIndices(vertexCount);
         case DEBUG_LINE_STRIP -> genDebugLineStripIndices(vertexCount);
         default -> throw new IllegalArgumentException("Unsupported drawType: %s".formatted(this.drawType));
         case LINES -> {
            if (indexType == IndexBuffer.IndexType.UINT16) {
               yield genLinesIndices(vertexCount);
            } else {
               yield genIntLinesIndices(vertexCount);
            }
         }
      };
      int size = buffer.capacity();
      this.indexBuffer = new IndexBuffer(size, MemoryTypes.GPU_MEM, indexType);
      this.indexBuffer.copyBuffer(buffer, buffer.remaining());
      MemoryUtil.memFree(buffer);
   }

   public void checkCapacity(int vertexCount) {
      if (vertexCount > this.vertexCount) {
         int newVertexCount = Math.max(this.vertexCount * 2, vertexCount);
         Initializer.LOGGER.info("Reallocating AutoIndexBuffer from {} to {}", this.vertexCount, newVertexCount);
         this.indexBuffer.scheduleFree();
         this.createIndexBuffer(newVertexCount);
      }
   }

   public IndexBuffer getIndexBuffer() {
      return this.indexBuffer;
   }

   public void freeBuffer() {
      this.indexBuffer.scheduleFree();
   }

   public int getIndexCount(int vertexCount) {
      return getIndexCount(this.drawType, vertexCount);
   }

   public static int getIndexCount(AutoIndexBuffer.DrawType drawType, int vertexCount) {
      switch (drawType) {
         case QUADS:
         case LINES:
            return vertexCount * 3 / 2;
         case TRIANGLE_FAN:
         case TRIANGLE_STRIP:
            return (vertexCount - 2) * 3;
         case DEBUG_LINE_STRIP:
            return (vertexCount - 1) * 2;
         case DEBUG_LINES:
         default:
            throw new RuntimeException(String.format("unknown drawMode: %s", drawType));
      }
   }

   public static int maxVertexCount(AutoIndexBuffer.DrawType drawType, int maxIndexCount) {
      return switch (drawType) {
         case QUADS, LINES -> maxIndexCount * 3 / 2;
         default -> maxIndexCount;
      };
   }

   public static ByteBuffer genQuadIndices(int vertexCount) {
      int indexCount = vertexCount * 3 / 2;
      indexCount = roundUpToDivisible(indexCount, 6);
      ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 2);
      ShortBuffer idxs = buffer.asShortBuffer();
      int j = 0;

      for (int i = 0; i < vertexCount; i += 4) {
         idxs.put(j + 0, (short)i);
         idxs.put(j + 1, (short)(i + 1));
         idxs.put(j + 2, (short)(i + 2));
         idxs.put(j + 3, (short)i);
         idxs.put(j + 4, (short)(i + 2));
         idxs.put(j + 5, (short)(i + 3));
         j += 6;
      }

      return buffer;
   }

   public static ByteBuffer genIntQuadIndices(int vertexCount) {
      int indexCount = vertexCount * 3 / 2;
      indexCount = roundUpToDivisible(indexCount, 6);
      ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 4);
      IntBuffer idxs = buffer.asIntBuffer();
      int j = 0;

      for (int i = 0; i < vertexCount; i += 4) {
         idxs.put(j + 0, i);
         idxs.put(j + 1, i + 1);
         idxs.put(j + 2, i + 2);
         idxs.put(j + 3, i);
         idxs.put(j + 4, i + 2);
         idxs.put(j + 5, i + 3);
         j += 6;
      }

      return buffer;
   }

   public static ByteBuffer genLinesIndices(int vertexCount) {
      int indexCount = vertexCount * 3 / 2;
      indexCount = roundUpToDivisible(indexCount, 6);
      ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 2);
      ShortBuffer idxs = buffer.asShortBuffer();
      int j = 0;

      for (int i = 0; i < vertexCount; i += 4) {
         idxs.put(j + 0, (short)i);
         idxs.put(j + 1, (short)(i + 1));
         idxs.put(j + 2, (short)(i + 2));
         idxs.put(j + 3, (short)(i + 3));
         idxs.put(j + 4, (short)(i + 2));
         idxs.put(j + 5, (short)(i + 1));
         j += 6;
      }

      return buffer;
   }

   public static ByteBuffer genIntLinesIndices(int vertexCount) {
      int indexCount = vertexCount * 3 / 2;
      indexCount = roundUpToDivisible(indexCount, 6);
      ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 4);
      IntBuffer idxs = buffer.asIntBuffer();
      int j = 0;

      for (int i = 0; i < vertexCount; i += 4) {
         idxs.put(j + 0, i);
         idxs.put(j + 1, i + 1);
         idxs.put(j + 2, i + 2);
         idxs.put(j + 3, i + 3);
         idxs.put(j + 4, i + 2);
         idxs.put(j + 5, i + 1);
         j += 6;
      }

      return buffer;
   }

   public static ByteBuffer genTriangleFanIndices(int vertexCount) {
      int indexCount = (vertexCount - 2) * 3;
      ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 2);
      ShortBuffer idxs = buffer.asShortBuffer();
      int j = 0;

      for (int i = 0; i < vertexCount - 2; i++) {
         idxs.put(j + 0, (short)0);
         idxs.put(j + 1, (short)(i + 1));
         idxs.put(j + 2, (short)(i + 2));
         j += 3;
      }

      return buffer;
   }

   public static ByteBuffer genTriangleStripIndices(int vertexCount) {
      int indexCount = (vertexCount - 2) * 3;
      ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 2);
      ShortBuffer idxs = buffer.asShortBuffer();
      int j = 0;

      for (int i = 0; i < vertexCount - 2; i++) {
         idxs.put(j + 0, (short)i);
         idxs.put(j + 1, (short)(i + 1));
         idxs.put(j + 2, (short)(i + 2));
         j += 3;
      }

      return buffer;
   }

   public static ByteBuffer genDebugLineStripIndices(int vertexCount) {
      int indexCount = (vertexCount - 1) * 2;
      ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 2);
      ShortBuffer idxs = buffer.asShortBuffer();
      int j = 0;

      for (int i = 0; i < vertexCount - 1; i++) {
         idxs.put(j + 0, (short)i);
         idxs.put(j + 1, (short)(i + 1));
         j += 2;
      }

      return buffer;
   }

   public static int roundUpToDivisible(int n, int d) {
      return (n + d - 1) / d * d;
   }

   public enum DrawType {
      QUADS(7),
      TRIANGLE_FAN(6),
      TRIANGLE_STRIP(5),
      DEBUG_LINE_STRIP(4),
      DEBUG_LINES(3),
      LINES(1);

      public final int n;

      DrawType(int n) {
         this.n = n;
      }

      public static int getQuadIndexCount(int vertexCount) {
         return vertexCount * 3 / 2;
      }

      public static int getTriangleStripIndexCount(int vertexCount) {
         return (vertexCount - 2) * 3;
      }
   }
}
