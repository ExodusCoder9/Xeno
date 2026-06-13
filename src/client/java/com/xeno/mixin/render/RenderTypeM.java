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
package com.xeno.mixin.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.xeno.interfaces.ExtendedRenderType;
import com.xeno.render.engine.VkCommandEncoder;
import com.xeno.render.engine.VkRenderPass;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class RenderTypeM implements ExtendedRenderType {
   @Unique
   TerrainRenderType terrainRenderType;
   @Shadow
   @Final
   private RenderSetup state;
   @Shadow
   @Final
   protected String name;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void inj(String string, RenderSetup renderSetup, CallbackInfo ci) {
      this.terrainRenderType = switch (string) {
         case "solid" -> TerrainRenderType.SOLID;
         case "cutout" -> TerrainRenderType.CUTOUT;
         case "translucent" -> TerrainRenderType.TRANSLUCENT;
         case "tripwire" -> TerrainRenderType.TRIPWIRE;
         default -> null;
      };
   }

   @Override
   public TerrainRenderType getTerrainRenderType() {
      return this.terrainRenderType;
   }

   @Overwrite
   public void draw(MeshData meshData) {
      Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
      RenderSetupAccessor renderSetupAccessor = (RenderSetupAccessor)(Object)this.state;
      Consumer<Matrix4fStack> consumer = renderSetupAccessor.layeringTransform().getModifier();
      if (consumer != null) {
         matrix4fStack.pushMatrix();
         consumer.accept(matrix4fStack);
      }

      GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
         .writeTransform(
            RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), renderSetupAccessor.textureTransform().getMatrix()
         );
      Map<String, RenderSetup.TextureAndSampler> map = this.state.getTextures();
      GpuBuffer gpuBuffer = renderSetupAccessor.pipeline().getVertexFormat().uploadImmediateVertexBuffer(meshData.vertexBuffer());
      if (meshData.indexBuffer() == null) {
         RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
         GpuBuffer gpuBuffer2 = autoStorageIndexBuffer.getBuffer(meshData.drawState().indexCount());
         VertexFormat.IndexType indexType = autoStorageIndexBuffer.type();
      } else {
         GpuBuffer gpuBuffer2 = renderSetupAccessor.pipeline().getVertexFormat().uploadImmediateIndexBuffer(meshData.indexBuffer());
         VertexFormat.IndexType indexType = meshData.drawState().indexType();
      }

      RenderTarget renderTarget = renderSetupAccessor.outputTarget().getRenderTarget();
      GpuTextureView gpuTextureView = RenderSystem.outputColorTextureOverride != null
         ? RenderSystem.outputColorTextureOverride
         : renderTarget.getColorTextureView();
      GpuTextureView gpuTextureView2 = renderTarget.useDepth
         ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : renderTarget.getDepthTextureView())
         : null;

      try (RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Immediate draw for " + this.name, gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
         renderPass.setPipeline(renderSetupAccessor.pipeline());
         ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
         if (scissorState.enabled()) {
            renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
         }

         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
         renderPass.setVertexBuffer(0, gpuBuffer);

         for (Entry<String, RenderSetup.TextureAndSampler> entry : map.entrySet()) {
            renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
         }

         VRenderSystem.applyModelViewMatrix(RenderSystem.getModelViewMatrix());
         VRenderSystem.calculateMVP();
         VkCommandEncoder commandEncoder = (VkCommandEncoder)RenderSystem.getDevice().createCommandEncoder().backend;
         commandEncoder.trySetup((VkRenderPass)renderPass.backend);
         Renderer.getDrawer()
            .draw(
               meshData.vertexBuffer(), meshData.indexBuffer(), meshData.drawState().mode(), meshData.drawState().format(), meshData.drawState().vertexCount()
            );
      }

      if (meshData != null) {
         meshData.close();
      }

      if (consumer != null) {
         matrix4fStack.popMatrix();
      }
   }
}
