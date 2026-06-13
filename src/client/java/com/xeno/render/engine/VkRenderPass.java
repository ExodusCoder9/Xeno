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
package com.xeno.render.engine;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import com.xeno.interfaces.shader.ExtendedRenderPipeline;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VkRenderPass implements RenderPassBackend {
   protected static final int MAX_VERTEX_BUFFERS = 1;
   public static final boolean VALIDATION = SharedConstants.IS_RUNNING_IN_IDE;
   private final VkCommandEncoder encoder;
   private final boolean hasDepthTexture;
   private boolean closed;
   @Nullable
   protected RenderPipeline pipeline;
   protected final GpuBuffer[] vertexBuffers = new GpuBuffer[1];
   @Nullable
   protected GpuBuffer indexBuffer;
   protected VertexFormat.IndexType indexType = VertexFormat.IndexType.INT;
   private final ScissorState scissorState = new ScissorState();
   protected final HashMap<String, GpuBufferSlice> uniforms = new HashMap<>();
   protected final HashMap<String, VkRenderPass.TextureViewAndSampler> samplers = new HashMap<>();
   protected final Set<String> dirtyUniforms = new HashSet<>();
   protected int pushedDebugGroups;
   private final boolean autoManaged;

   public VkRenderPass(VkCommandEncoder commandEncoder, boolean hasDepthTexture, boolean autoManaged) {
      this.encoder = commandEncoder;
      this.hasDepthTexture = hasDepthTexture;
      this.autoManaged = autoManaged;
   }

   public boolean hasDepthTexture() {
      return this.hasDepthTexture;
   }

   @Override
   public void pushDebugGroup(Supplier<String> supplier) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.pushedDebugGroups++;
   }

   @Override
   public void popDebugGroup() {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      if (this.pushedDebugGroups == 0) {
         throw new IllegalStateException("Can't pop more debug groups than was pushed!");
      }

      this.pushedDebugGroups--;
   }

   @Override
   public void setPipeline(RenderPipeline renderPipeline) {
      if (this.pipeline == null || this.pipeline != renderPipeline) {
         this.dirtyUniforms.addAll(this.uniforms.keySet());
      }

      this.pipeline = renderPipeline;
      if (ExtendedRenderPipeline.of(renderPipeline).getPipeline() == null) {
         this.encoder.getDevice().compilePipeline(renderPipeline);
      }
   }

   @Override
   public void bindTexture(String string, @Nullable GpuTextureView gpuTextureView, @Nullable GpuSampler gpuSampler) {
      if (gpuSampler == null) {
         this.samplers.remove(string);
      } else {
         this.samplers.put(string, new VkRenderPass.TextureViewAndSampler((VkTextureView)gpuTextureView, (VkSampler)gpuSampler));
      }

      this.dirtyUniforms.add(string);
   }

   @Override
   public void setUniform(String string, GpuBuffer gpuBuffer) {
      this.uniforms.put(string, gpuBuffer.slice());
      this.dirtyUniforms.add(string);
   }

   @Override
   public void setUniform(String string, GpuBufferSlice gpuBufferSlice) {
      int i = this.encoder.getDevice().getUniformOffsetAlignment();
      if (gpuBufferSlice.offset() % i > 0L) {
         throw new IllegalArgumentException("Uniform buffer offset must be aligned to " + i);
      }

      this.uniforms.put(string, gpuBufferSlice);
      this.dirtyUniforms.add(string);
   }

   @Override
   public void enableScissor(int i, int j, int k, int l) {
      this.scissorState.enable(i, j, k, l);
   }

   @Override
   public void disableScissor() {
      this.scissorState.disable();
   }

   public boolean isScissorEnabled() {
      return this.scissorState.enabled();
   }

   public int getScissorX() {
      return this.scissorState.x();
   }

   public int getScissorY() {
      return this.scissorState.y();
   }

   public int getScissorWidth() {
      return this.scissorState.width();
   }

   public int getScissorHeight() {
      return this.scissorState.height();
   }

   public ScissorState getScissorState() {
      return this.scissorState;
   }

   @Override
   public void setVertexBuffer(int i, GpuBuffer gpuBuffer) {
      if (i >= 0 && i < 1) {
         this.vertexBuffers[i] = gpuBuffer;
      } else {
         throw new IllegalArgumentException("Vertex buffer slot is out of range: " + i);
      }
   }

   @Override
   public void setIndexBuffer(@Nullable GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
      this.indexBuffer = gpuBuffer;
      this.indexType = indexType;
   }

   @Override
   public void drawIndexed(int vertexOffset, int firstIndex, int vertexCount, int instanceCount) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.encoder.executeDraw(this, vertexOffset, firstIndex, vertexCount, this.indexType, instanceCount);
   }

   @Override
   public <T> void drawMultipleIndexed(
      Collection<RenderPass.Draw<T>> collection,
      @Nullable GpuBuffer gpuBuffer,
      @Nullable VertexFormat.IndexType indexType,
      Collection<String> collection2,
      T object
   ) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.encoder.executeDrawMultiple(this, collection, gpuBuffer, indexType, collection2, object);
   }

   @Override
   public void draw(int vertexOffset, int vertexCount) {
      if (this.closed) {
         throw new IllegalStateException("Can't use a closed render pass");
      }

      this.encoder.executeDraw(this, vertexOffset, 0, vertexCount, null, 1);
   }

   @Override
   public void close() {
      if (!this.closed) {
         if (this.pushedDebugGroups > 0) {
            throw new IllegalStateException("Render pass had debug groups left open!");
         }

         this.closed = true;
         this.encoder.finishRenderPass(!this.autoManaged);
      }
   }

   @Override
   public boolean isClosed() {
      return this.closed;
   }

   @Nullable
   public RenderPipeline getPipeline() {
      return this.pipeline;
   }

   @Environment(EnvType.CLIENT)
   protected record TextureViewAndSampler(VkTextureView view, VkSampler sampler) {
   }
}
