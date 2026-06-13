package com.xeno.render;

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


import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.nio.ByteBuffer;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.memory.MemoryType;
import com.xeno.vulkan.memory.MemoryTypes;
import com.xeno.vulkan.memory.buffer.IndexBuffer;
import com.xeno.vulkan.memory.buffer.VertexBuffer;
import com.xeno.vulkan.memory.buffer.index.AutoIndexBuffer;
import com.xeno.vulkan.shader.GraphicsPipeline;
import com.xeno.vulkan.shader.Pipeline;
import com.xeno.vulkan.texture.VTextureSelector;

public class VBO {
   private final MemoryType memoryType;
   private VertexBuffer vertexBuffer;
   private IndexBuffer indexBuffer;
   private Mode mode;
   private boolean autoIndexed = false;
   private int indexCount;
   private int vertexCount;

   public VBO(boolean useGpuMem) {
      this.memoryType = useGpuMem ? MemoryTypes.GPU_MEM : MemoryTypes.HOST_MEM;
   }

   public void upload(MeshData meshData) {
      DrawState parameters = meshData.drawState();
      this.indexCount = parameters.indexCount();
      this.vertexCount = parameters.vertexCount();
      this.mode = parameters.mode();
      this.uploadVertexBuffer(parameters, meshData.vertexBuffer());
      this.uploadIndexBuffer(meshData.indexBuffer());
      meshData.close();
   }

   private void uploadVertexBuffer(DrawState parameters, ByteBuffer data) {
      if (data != null) {
         if (this.vertexBuffer != null) {
            this.vertexBuffer.scheduleFree();
         }

         int size = parameters.format().getVertexSize() * parameters.vertexCount();
         this.vertexBuffer = new VertexBuffer(size, this.memoryType);
         this.vertexBuffer.copyBuffer(data, size);
      }
   }

   public void uploadIndexBuffer(ByteBuffer data) {
      if (data == null) {
         if (this.indexBuffer != null && !this.autoIndexed) {
            this.indexBuffer.scheduleFree();
         }

         this.autoIndexed = true;
      } else {
         if (this.indexBuffer != null && !this.autoIndexed) {
            this.indexBuffer.scheduleFree();
         }

         this.indexBuffer = new IndexBuffer(data.remaining(), MemoryTypes.GPU_MEM);
         this.indexBuffer.copyBuffer(data, data.remaining());
      }
   }

   private IndexBuffer getAutoIndexBuffer() {
      AutoIndexBuffer autoIndexBuffer;
      switch (this.mode) {
         case TRIANGLE_FAN:
            autoIndexBuffer = Renderer.getDrawer().getTriangleFanIndexBuffer();
            this.indexCount = AutoIndexBuffer.DrawType.getTriangleStripIndexCount(this.vertexCount);
            break;
         case TRIANGLE_STRIP:
            autoIndexBuffer = Renderer.getDrawer().getTriangleStripIndexBuffer();
            this.indexCount = AutoIndexBuffer.DrawType.getTriangleStripIndexCount(this.vertexCount);
            break;
         case QUADS:
            autoIndexBuffer = Renderer.getDrawer().getQuadsIndexBuffer();
            break;
         case LINES:
            autoIndexBuffer = Renderer.getDrawer().getLinesIndexBuffer();
            break;
         case DEBUG_LINE_STRIP:
            autoIndexBuffer = Renderer.getDrawer().getDebugLineStripIndexBuffer();
            break;
         case TRIANGLES:
         case DEBUG_LINES:
            autoIndexBuffer = null;
            break;
         default:
            throw new IllegalStateException("Unexpected draw mode: %s".formatted(this.mode));
      }

      if (autoIndexBuffer != null) {
         autoIndexBuffer.checkCapacity(this.vertexCount);
         return autoIndexBuffer.getIndexBuffer();
      } else {
         return null;
      }
   }

   public void bind(GraphicsPipeline pipeline) {
      Renderer renderer = Renderer.getInstance();
      renderer.bindGraphicsPipeline(pipeline);
      VTextureSelector.bindShaderTextures(pipeline);
      renderer.uploadAndBindUBOs(pipeline);
   }

   public void draw() {
      if (this.indexCount != 0) {
         Renderer renderer = Renderer.getInstance();
         Pipeline pipeline = renderer.getBoundPipeline();
         renderer.uploadAndBindUBOs(pipeline);
         if (this.autoIndexed) {
            this.indexBuffer = this.getAutoIndexBuffer();
         }

         if (this.indexBuffer != null) {
            Renderer.getDrawer().drawIndexed(this.vertexBuffer, this.indexBuffer, this.indexCount);
         } else {
            Renderer.getDrawer().draw(this.vertexBuffer, this.vertexCount);
         }
      }
   }

   public void close() {
      if (this.vertexCount > 0) {
         this.vertexBuffer.scheduleFree();
         this.vertexBuffer = null;
         if (!this.autoIndexed) {
            this.indexBuffer.scheduleFree();
            this.indexBuffer = null;
         }

         this.vertexCount = 0;
         this.indexCount = 0;
      }
   }

   public VertexBuffer getVertexBuffer() {
      return this.vertexBuffer;
   }

   public int getVertexCount() {
      return this.vertexCount;
   }

   public int getIndexCount() {
      return this.indexCount;
   }

   public boolean isAutoIndexed() {
      return this.autoIndexed;
   }

   public Mode getMode() {
      return this.mode;
   }

   public IndexBuffer getIndexBuffer() {
      return this.indexBuffer;
   }
}
