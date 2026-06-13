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


import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import java.nio.ByteBuffer;
import net.minecraft.world.level.block.state.BlockState;
import com.xeno.Initializer;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.shader.PipelineManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryUtil.MemoryAllocator;

public class TerrainBuilder {
   private static final Logger LOGGER = Initializer.LOGGER;
   private static final MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);
   protected long indexBufferPtr;
   private int indexBufferCapacity;
   private final VertexFormat format;
   private boolean building;
   private final QuadSorter quadSorter = new QuadSorter();
   private boolean needsSorting;
   private boolean indexOnly;
   protected VertexBuilder vertexBuilder;
   private final TerrainBufferBuilder[] bufferBuilders;

   public TerrainBuilder(int size, VertexBuilder vertexBuilder) {
      this.indexBufferCapacity = size;
      this.indexBufferPtr = ALLOCATOR.malloc(this.indexBufferCapacity);
      this.format = PipelineManager.terrainVertexFormat;
      this.vertexBuilder = vertexBuilder;
      TerrainBufferBuilder[] bufferBuilders = new TerrainBufferBuilder[QuadFacing.COUNT];

      for (int i = 0; i < QuadFacing.COUNT; i++) {
         bufferBuilders[i] = new TerrainBufferBuilder(size, this.format.getVertexSize(), this.vertexBuilder);
      }

      this.bufferBuilders = bufferBuilders;
   }

   public TerrainBufferBuilder getBufferBuilder(int i) {
      return this.bufferBuilders[i];
   }

   private void ensureIndexCapacity(int size) {
      if (size > this.indexBufferCapacity) {
         int capacity = this.indexBufferCapacity;
         int newSize = (capacity + size) * 2;
         this.resizeIndexBuffer(newSize);
      }
   }

   private void resizeIndexBuffer(int i) {
      this.indexBufferPtr = ALLOCATOR.realloc(this.indexBufferPtr, i);
      LOGGER.debug("Needed to grow index buffer: Old size {} bytes, new size {} bytes.", this.indexBufferCapacity, i);
      if (this.indexBufferPtr == 0L) {
         throw new OutOfMemoryError("Failed to resize buffer from " + this.indexBufferCapacity + " bytes to " + i + " bytes");
      }

      this.indexBufferCapacity = i;
   }

   public void setupQuadSorting(float x, float y, float z) {
      this.quadSorter.setQuadSortOrigin(x, y, z);
      this.needsSorting = true;
   }

   public QuadSorter.SortState getSortState() {
      return this.quadSorter.getSortState();
   }

   public void restoreSortState(QuadSorter.SortState sortState) {
      this.quadSorter.restoreSortState(sortState);
      this.indexOnly = true;
   }

   public void setIndexOnly() {
      this.indexOnly = true;
   }

   public void begin() {
      if (this.building) {
         throw new IllegalStateException("Already building!");
      }

      this.building = true;
   }

   public void setupQuadSortingPoints() {
      TerrainBufferBuilder bufferBuilder = this.bufferBuilders[QuadFacing.UNDEFINED.ordinal()];
      long bufferPtr = bufferBuilder.getPtr();
      int vertexCount = bufferBuilder.getVertices();
      this.quadSorter.setupQuadSortingPoints(bufferPtr, vertexCount, this.format);
   }

   public TerrainBuilder.DrawState endDrawing() {
      for (TerrainBufferBuilder bufferBuilder : this.bufferBuilders) {
         bufferBuilder.end();
      }

      int vertexCount = this.quadSorter.getVertexCount();
      int indexCount = vertexCount / 4 * 6;
      IndexType indexType = IndexType.least(indexCount);
      boolean sequentialIndexing;
      if (this.needsSorting) {
         int indexBufferSize = indexCount * indexType.bytes;
         this.ensureIndexCapacity(indexBufferSize);
         this.quadSorter.putSortedQuadIndices(this, indexType);
         sequentialIndexing = false;
      } else {
         sequentialIndexing = true;
      }

      return new TerrainBuilder.DrawState(this.format.getVertexSize(), indexCount, indexType, this.indexOnly, sequentialIndexing);
   }

   public ByteBuffer getIndexBuffer() {
      int indexCount = this.quadSorter.getVertexCount() * 6 / 4;
      return MemoryUtil.memByteBuffer(this.indexBufferPtr, indexCount * 2);
   }

   private void ensureDrawing() {
      if (!this.building) {
         throw new IllegalStateException("Not building!");
      }
   }

   public void reset() {
      this.building = false;
      this.indexOnly = false;
      this.needsSorting = false;
   }

   public void clear() {
      this.reset();

      for (TerrainBufferBuilder bufferBuilder : this.bufferBuilders) {
         bufferBuilder.clear();
      }
   }

   public void free() {
      ALLOCATOR.free(this.indexBufferPtr);

      for (TerrainBufferBuilder bufferBuilder : this.bufferBuilders) {
         bufferBuilder.free();
      }
   }

   public void setBlockAttributes(BlockState blockState) {
   }

   public record DrawState(int vertexSize, int indexCount, IndexType indexType, boolean indexOnly, boolean sequentialIndex) {
      public DrawState {
      }

      private int indexBufferSize() {
         return this.sequentialIndex ? 0 : this.indexCount * this.indexType.bytes;
      }
   }
}
