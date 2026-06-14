package com.xeno.vulkan;

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
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import com.xeno.render.engine.VkGpuBuffer;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.memory.MemoryTypes;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.memory.buffer.IndexBuffer;
import com.xeno.vulkan.memory.buffer.UniformBuffer;
import com.xeno.vulkan.memory.buffer.VertexBuffer;
import com.xeno.vulkan.memory.buffer.index.AutoIndexBuffer;
import com.xeno.vulkan.util.MemoryAccess;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

public class Drawer {
   private static final int INITIAL_VB_SIZE = 4000000;
   private static final int INITIAL_IB_SIZE = 1000000;
   private static final int INITIAL_UB_SIZE = 200000;
   private static final LongBuffer buffers = MemoryUtil.memAllocLong(1);
   private static final LongBuffer offsets = MemoryUtil.memAllocLong(1);
   private static final long pBuffers = MemoryUtil.memAddress0(buffers);
   private static final long pOffsets = MemoryUtil.memAddress0(offsets);
   private int framesNum;
   private VertexBuffer[] vertexBuffers;
   private IndexBuffer[] indexBuffers;
   private final Drawer.GpuBuffers gpuBuffers = new Drawer.GpuBuffers();
   private final AutoIndexBuffer quadsIndexBuffer = new AutoIndexBuffer(65536, AutoIndexBuffer.DrawType.QUADS);
   private final AutoIndexBuffer quadsIntIndexBuffer = new AutoIndexBuffer(100000, AutoIndexBuffer.DrawType.QUADS);
   private final AutoIndexBuffer linesIndexBuffer = new AutoIndexBuffer(10000, AutoIndexBuffer.DrawType.LINES);
   private final AutoIndexBuffer debugLineStripIndexBuffer = new AutoIndexBuffer(10000, AutoIndexBuffer.DrawType.DEBUG_LINE_STRIP);
   private final AutoIndexBuffer triangleFanIndexBuffer = new AutoIndexBuffer(1000, AutoIndexBuffer.DrawType.TRIANGLE_FAN);
   private final AutoIndexBuffer triangleStripIndexBuffer = new AutoIndexBuffer(10000, AutoIndexBuffer.DrawType.TRIANGLE_STRIP);
   private UniformBuffer[] uniformBuffers;
   private int currentFrame;

   public Drawer() {
   }

   public void setCurrentFrame(int currentFrame) {
      this.currentFrame = currentFrame;
   }

   public void createResources(int framesNum) {
      this.framesNum = framesNum;
      if (this.vertexBuffers != null) {
         Arrays.stream(this.vertexBuffers).iterator().forEachRemaining(Buffer::scheduleFree);
      }

      this.vertexBuffers = new VertexBuffer[framesNum];
      Arrays.setAll(this.vertexBuffers, i -> new VertexBuffer(4000000, MemoryTypes.HOST_MEM));
      if (this.indexBuffers != null) {
         Arrays.stream(this.indexBuffers).iterator().forEachRemaining(Buffer::scheduleFree);
      }

      this.indexBuffers = new IndexBuffer[framesNum];
      Arrays.setAll(this.indexBuffers, i -> new IndexBuffer(1000000, MemoryTypes.HOST_MEM));
      if (this.uniformBuffers != null) {
         Arrays.stream(this.uniformBuffers).iterator().forEachRemaining(Buffer::scheduleFree);
      }

      this.uniformBuffers = new UniformBuffer[framesNum];
      Arrays.setAll(this.uniformBuffers, i -> new UniformBuffer(200000, MemoryTypes.HOST_MEM));
      this.getGpuBuffers().createBuffers(this.vertexBuffers, this.indexBuffers);
   }

   public void resetBuffers(int currentFrame) {
      this.vertexBuffers[currentFrame].reset();
      this.indexBuffers[currentFrame].reset();
      this.uniformBuffers[currentFrame].reset();
   }

   public void draw(ByteBuffer vertexData, Mode mode, VertexFormat vertexFormat, int vertexCount) {
      this.draw(vertexData, null, mode, vertexFormat, vertexCount);
   }

   public void draw(ByteBuffer vertexData, ByteBuffer indexData, Mode mode, VertexFormat vertexFormat, int vertexCount) {
      VertexBuffer vertexBuffer = this.vertexBuffers[this.currentFrame];
      int size = vertexFormat.getVertexSize() * vertexCount;
      vertexBuffer.copyBuffer(vertexData, size);
      if (indexData != null) {
         IndexBuffer indexBuffer = this.indexBuffers[this.currentFrame];
         indexBuffer.copyBuffer(indexData, indexData.remaining());
         int indexCount = vertexCount * 3 / 2;
         this.drawIndexed(vertexBuffer, indexBuffer, indexCount);
      } else {
         AutoIndexBuffer autoIndexBuffer = this.getAutoIndexBuffer(mode, vertexCount);
         if (autoIndexBuffer != null) {
            int indexCount = autoIndexBuffer.getIndexCount(vertexCount);
            autoIndexBuffer.checkCapacity(vertexCount);
            this.drawIndexed(vertexBuffer, autoIndexBuffer.getIndexBuffer(), indexCount);
         } else {
            this.draw(vertexBuffer, vertexCount);
         }
      }
   }

   public void drawIndexed(Buffer vertexBuffer, IndexBuffer indexBuffer, int indexCount) {
      this.drawIndexed(vertexBuffer, indexBuffer, indexCount, indexBuffer.indexType.value);
   }

   public void drawIndexed(Buffer vertexBuffer, Buffer indexBuffer, int indexCount, int indexType) {
      VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
      MemoryAccess.memPutLong(pBuffers, vertexBuffer.getId());
      MemoryAccess.memPutLong(pOffsets, vertexBuffer.getOffset());
      VK10.nvkCmdBindVertexBuffers(commandBuffer, 0, 1, pBuffers, pOffsets);
      this.bindIndexBuffer(commandBuffer, indexBuffer, indexType);
      VK10.vkCmdDrawIndexed(commandBuffer, indexCount, 1, 0, 0, 0);
   }

   public void draw(VertexBuffer vertexBuffer, int vertexCount) {
      VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
      MemoryAccess.memPutLong(pBuffers, vertexBuffer.getId());
      MemoryAccess.memPutLong(pOffsets, vertexBuffer.getOffset());
      VK10.nvkCmdBindVertexBuffers(commandBuffer, 0, 1, pBuffers, pOffsets);
      VK10.vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
   }

   public void bindIndexBuffer(VkCommandBuffer commandBuffer, Buffer indexBuffer, int indexType) {
      VK10.vkCmdBindIndexBuffer(commandBuffer, indexBuffer.getId(), indexBuffer.getOffset(), indexType);
   }

   public void cleanUpResources() {
      for (int i = 0; i < this.framesNum; i++) {
         Buffer buffer = this.vertexBuffers[i];
         MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());
         buffer = this.indexBuffers[i];
         MemoryManager.freeBuffer(buffer.getId(), buffer.getAllocation());
         Buffer var4 = this.uniformBuffers[i];
         MemoryManager.freeBuffer(var4.getId(), var4.getAllocation());
      }

      this.quadsIndexBuffer.freeBuffer();
      this.quadsIntIndexBuffer.freeBuffer();
      this.linesIndexBuffer.freeBuffer();
      this.triangleFanIndexBuffer.freeBuffer();
      this.triangleStripIndexBuffer.freeBuffer();
      this.debugLineStripIndexBuffer.freeBuffer();
   }

    public record DrawData(VertexBuffer vertexBuffer, long vertexOffset, Buffer indexBuffer, long indexOffset, int indexCount, int indexType) {}

    public DrawData uploadData(ByteBuffer vertexData, ByteBuffer indexData, Mode mode, VertexFormat vertexFormat, int vertexCount) {
       VertexBuffer vertexBuffer = this.vertexBuffers[this.currentFrame];
       int size = vertexFormat.getVertexSize() * vertexCount;
       vertexBuffer.copyBuffer(vertexData, size);
       long vertexOffset = vertexBuffer.getOffset();
       if (indexData != null) {
          IndexBuffer indexBuffer = this.indexBuffers[this.currentFrame];
          indexBuffer.copyBuffer(indexData, indexData.remaining());
          int indexCount = vertexCount * 3 / 2;
          return new DrawData(vertexBuffer, vertexOffset, indexBuffer, indexBuffer.getOffset(), indexCount, indexBuffer.indexType.value);
       }
       AutoIndexBuffer autoIndexBuffer = this.getAutoIndexBuffer(mode, vertexCount);
       if (autoIndexBuffer != null) {
          int indexCount = autoIndexBuffer.getIndexCount(vertexCount);
          autoIndexBuffer.checkCapacity(vertexCount);
          return new DrawData(vertexBuffer, vertexOffset, autoIndexBuffer.getIndexBuffer(), 0L, indexCount, autoIndexBuffer.getIndexBuffer().indexType.value);
       }
       return new DrawData(vertexBuffer, vertexOffset, null, -1L, vertexCount, 0);
    }

    public void drawPrepared(VertexBuffer vertexBuffer, long vertexOffset, Buffer indexBuffer, long indexOffset, int indexCount, int indexType) {
       VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
       MemoryAccess.memPutLong(pBuffers, vertexBuffer.getId());
       MemoryAccess.memPutLong(pOffsets, vertexOffset);
       VK10.nvkCmdBindVertexBuffers(commandBuffer, 0, 1, pBuffers, pOffsets);
       if (indexBuffer != null && indexOffset >= 0L) {
          VK10.vkCmdBindIndexBuffer(commandBuffer, indexBuffer.getId(), indexOffset, indexType);
          VK10.vkCmdDrawIndexed(commandBuffer, indexCount, 1, 0, 0, 0);
       } else {
          VK10.vkCmdDraw(commandBuffer, indexCount, 1, 0, 0);
       }
    }

    public AutoIndexBuffer getQuadsIndexBuffer() {
       return this.quadsIndexBuffer;
    }

   public AutoIndexBuffer getLinesIndexBuffer() {
      return this.linesIndexBuffer;
   }

   public AutoIndexBuffer getTriangleFanIndexBuffer() {
      return this.triangleFanIndexBuffer;
   }

   public AutoIndexBuffer getTriangleStripIndexBuffer() {
      return this.triangleStripIndexBuffer;
   }

   public AutoIndexBuffer getDebugLineStripIndexBuffer() {
      return this.debugLineStripIndexBuffer;
   }

   public UniformBuffer getUniformBuffer() {
      return this.uniformBuffers[this.currentFrame];
   }

   public AutoIndexBuffer getAutoIndexBuffer(Mode mode, int vertexCount) {
      return switch (mode) {
         case QUADS -> {
            int indexCount = vertexCount * 3 / 2;
            yield indexCount > 65536 ? this.quadsIntIndexBuffer : this.quadsIndexBuffer;
         }
         case LINES -> this.linesIndexBuffer;
         case TRIANGLE_FAN -> this.triangleFanIndexBuffer;
         case TRIANGLE_STRIP -> this.triangleStripIndexBuffer;
         case DEBUG_LINE_STRIP -> this.debugLineStripIndexBuffer;
         case POINTS -> null;
         case TRIANGLES, DEBUG_LINES -> null;
         default -> throw new MatchException(null, null);
      };
   }

   public Drawer.GpuBuffers getGpuBuffers() {
      return this.gpuBuffers;
   }

   public class GpuBuffers {
      VkGpuBuffer[] vertexBuffers;
      VkGpuBuffer[] indexBuffers;

      public GpuBuffers() {
      }

      public void createBuffers(VertexBuffer[] vertexBuffers, IndexBuffer[] indexBuffers) {
         this.vertexBuffers = new VkGpuBuffer[vertexBuffers.length];

         for (int i = 0; i < vertexBuffers.length; i++) {
            this.vertexBuffers[i] = VkGpuBuffer.from(vertexBuffers[i]);
         }

         this.indexBuffers = new VkGpuBuffer[indexBuffers.length];

         for (int i = 0; i < vertexBuffers.length; i++) {
            this.indexBuffers[i] = VkGpuBuffer.from(indexBuffers[i]);
         }
      }

      public VkGpuBuffer getVertexBuffer() {
         return this.vertexBuffers[Drawer.this.currentFrame];
      }

      public VkGpuBuffer getIndexBuffer() {
         return this.indexBuffers[Drawer.this.currentFrame];
      }
   }
}
