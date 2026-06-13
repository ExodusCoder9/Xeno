package com.xeno.render.chunk.build;

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
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.chunk.util.BufferUtil;
import com.xeno.render.vertex.TerrainBufferBuilder;
import com.xeno.render.vertex.TerrainBuilder;
import org.lwjgl.system.MemoryUtil;

public class UploadBuffer {
   public final int indexCount;
   public final boolean autoIndices;
   public final boolean indexOnly;
   private final ByteBuffer[] vertexBuffers;
   private final ByteBuffer indexBuffer;

   public UploadBuffer(TerrainBuilder terrainBuilder, TerrainBuilder.DrawState drawState) {
      this.indexCount = drawState.indexCount();
      this.autoIndices = drawState.sequentialIndex();
      this.indexOnly = drawState.indexOnly();
      if (!this.indexOnly) {
         this.vertexBuffers = new ByteBuffer[QuadFacing.COUNT];

         for (int i = 0; i < QuadFacing.COUNT; i++) {
            TerrainBufferBuilder bufferBuilder = terrainBuilder.getBufferBuilder(i);
            if (bufferBuilder.getVertices() > 0) {
               this.vertexBuffers[i] = BufferUtil.clone(bufferBuilder.getBuffer());
            }
         }
      } else {
         this.vertexBuffers = null;
      }

      if (!drawState.sequentialIndex()) {
         this.indexBuffer = BufferUtil.clone(terrainBuilder.getIndexBuffer());
      } else {
         this.indexBuffer = null;
      }
   }

   public int indexCount() {
      return this.indexCount;
   }

   public ByteBuffer[] getVertexBuffers() {
      return this.vertexBuffers;
   }

   public ByteBuffer getIndexBuffer() {
      return this.indexBuffer;
   }

   public void release() {
      if (this.vertexBuffers != null) {
         for (ByteBuffer vertexBuffer : this.vertexBuffers) {
            if (vertexBuffer != null) {
               MemoryUtil.memFree(vertexBuffer);
            }
         }
      }

      if (this.indexBuffer != null) {
         MemoryUtil.memFree(this.indexBuffer);
      }
   }
}
