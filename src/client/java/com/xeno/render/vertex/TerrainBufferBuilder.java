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


import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import com.xeno.Initializer;
import com.xeno.render.vertex.format.I32_SNorm;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryUtil.MemoryAllocator;

public class TerrainBufferBuilder implements VertexConsumer {
   private static final Logger LOGGER = Initializer.LOGGER;
   private static final MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);
   private int capacity;
   private int vertexSize;
   protected long bufferPtr;
   protected int nextElementByte;
   int vertices;
   private long elementPtr;
   private VertexBuilder vertexBuilder;

   public TerrainBufferBuilder(int size, int vertexSize, VertexBuilder vertexBuilder) {
      this.bufferPtr = ALLOCATOR.malloc(size);
      this.capacity = size;
      this.vertexSize = vertexSize;
      this.vertexBuilder = vertexBuilder;
   }

   public void ensureCapacity() {
      this.ensureCapacity(this.vertexSize * 4);
   }

   private void ensureCapacity(int size) {
      if (this.nextElementByte + size > this.capacity) {
         int capacity = this.capacity;
         int newSize = (capacity + size) * 2;
         this.resize(newSize);
      }
   }

   private void resize(int i) {
      this.bufferPtr = ALLOCATOR.realloc(this.bufferPtr, i);
      LOGGER.debug("Needed to grow BufferBuilder buffer: Old size {} bytes, new size {} bytes.", this.capacity, i);
      if (this.bufferPtr == 0L) {
         throw new OutOfMemoryError("Failed to resize buffer from " + this.capacity + " bytes to " + i + " bytes");
      }

      this.capacity = i;
   }

   public void endVertex() {
      this.nextElementByte = this.nextElementByte + this.vertexSize;
      this.vertices++;
   }

   public void vertex(float x, float y, float z, int color, float u, float v, int light, int packedNormal) {
      long ptr = this.bufferPtr + this.nextElementByte;
      if (this.vertexBuilder instanceof VertexBuilder.CompressedVertexBuilder cvb) {
         cvb.vertex(ptr, x, y, z, color, u, v, light, packedNormal);
      } else {
         this.vertexBuilder.vertex(ptr, x, y, z, color, u, v, light, packedNormal);
      }
      // Inlined endVertex() to save a method call per vertex
      this.nextElementByte += this.vertexSize;
      this.vertices++;
   }

   public void end() {
   }

   public void clear() {
      this.nextElementByte = 0;
      this.vertices = 0;
   }

   public void free() {
      ALLOCATOR.free(this.bufferPtr);
   }

   public ByteBuffer getBuffer() {
      return MemoryUtil.memByteBuffer(this.bufferPtr, this.vertices * this.vertexSize);
   }

   public long getPtr() {
      return this.bufferPtr;
   }

   public int getVertices() {
      return this.vertices;
   }

   public int getNextElementByte() {
      return this.nextElementByte;
   }

   public VertexConsumer addVertex(float x, float y, float z) {
      this.elementPtr = this.bufferPtr + this.nextElementByte;
      this.endVertex();
      this.vertexBuilder.position(this.elementPtr, x, y, z);
      return this;
   }

   public VertexConsumer setColor(int r, int g, int b, int a) {
      int color = (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | r & 0xFF;
      this.vertexBuilder.color(this.elementPtr, color);
      return this;
   }

   public VertexConsumer setColor(int color) {
      this.vertexBuilder.color(this.elementPtr, color);
      return this;
   }

   public VertexConsumer setUv(float u, float v) {
      this.vertexBuilder.uv(this.elementPtr, u, v);
      return this;
   }

   public VertexConsumer setLight(int i) {
      this.vertexBuilder.light(this.elementPtr, i);
      return this;
   }

   public VertexConsumer setNormal(float f, float g, float h) {
      int packedNormal = I32_SNorm.packNormal(f, g, h);
      this.vertexBuilder.normal(this.elementPtr, packedNormal);
      return this;
   }

   public VertexConsumer setLineWidth(float f) {
      return this;
   }

   public VertexConsumer setUv1(int i, int j) {
      return this;
   }

   public VertexConsumer setUv2(int i, int j) {
      return this;
   }

   @Override
   public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
      this.vertex(x, y, z, color, u, v, light, I32_SNorm.packNormal(normalX, normalY, normalZ));
   }
}
