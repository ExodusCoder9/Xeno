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
package com.xeno.mixin.chunk;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.ChunkPos;
import com.xeno.render.chunk.WorldRenderer;
import com.xeno.render.profiling.Profiler;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.VRenderSystem;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
   @Shadow
   @Final
   private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;
   @Unique
   private WorldRenderer worldRenderer;
   @Unique
   double camX;
   @Unique
   double camY;
   @Unique
   double camZ;
   @Unique
   Camera camera;
   @Unique
   boolean spectator;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void init(
      Minecraft minecraft,
      EntityRenderDispatcher entityRenderDispatcher,
      BlockEntityRenderDispatcher blockEntityRenderDispatcher,
      RenderBuffers renderBuffers,
      GameRenderState gameRenderState,
      FeatureRenderDispatcher featureRenderDispatcher,
      CallbackInfo ci
   ) {
      this.worldRenderer = WorldRenderer.init(entityRenderDispatcher, blockEntityRenderDispatcher, renderBuffers, gameRenderState, featureRenderDispatcher);
   }

   @Inject(method = "setLevel", at = @At("RETURN"))
   private void setLevel(ClientLevel clientLevel, CallbackInfo ci) {
      this.worldRenderer.setLevel(clientLevel);
   }

   @Inject(method = "allChanged", at = @At("RETURN"))
   private void onAllChanged(CallbackInfo ci) {
      this.worldRenderer.allChanged();
   }

   @Inject(method = "extractVisibleBlockEntities", at = @At("HEAD"), cancellable = true)
   private void onExtractVisibleBlockEntities(Camera camera, float deltaPartialTick, LevelRenderState levelRenderState, CallbackInfo ci) {
      this.worldRenderer.setPartialTick(deltaPartialTick);
      ci.cancel();
   }

   @Inject(method = "submitBlockEntities", at = @At("RETURN"), cancellable = true)
   private void onSubmitBlockEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeStorage submitNodeStorage, CallbackInfo ci) {
      this.worldRenderer.renderBlockEntities(poseStack, levelRenderState, submitNodeStorage, this.destructionProgress);
      ci.cancel();
   }

   @Overwrite
   private void cullTerrain(Camera camera, Frustum frustum, boolean spectator) {
      this.camera = camera;
      this.spectator = spectator;
   }

   @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;pushMatrix()Lorg/joml/Matrix4fStack;"))
   private void setupRenderer(
      GraphicsResourceAllocator resourceAllocator,
      DeltaTracker deltaTracker,
      boolean renderOutline,
      CameraRenderState cameraState,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice terrainFog,
      Vector4f fogColor,
      boolean shouldRenderSky,
      ChunkSectionsToRender chunkSectionsToRender,
      CallbackInfo ci
   ) {
      this.worldRenderer.setupRenderer(this.camera, this.camera.getCullFrustum(), this.camera.getCapturedFrustum() != null, this.spectator);
   }

   @Overwrite
   public boolean isSectionCompiledAndVisible(BlockPos blockPos) {
      return this.worldRenderer.isSectionCompiled(blockPos);
   }

   @Inject(method = "renderLevel", at = @At("HEAD"))
   private void updateModelView(
      GraphicsResourceAllocator resourceAllocator,
      DeltaTracker deltaTracker,
      boolean renderOutline,
      CameraRenderState cameraState,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice terrainFog,
      Vector4f fogColor,
      boolean shouldRenderSky,
      ChunkSectionsToRender chunkSectionsToRender,
      CallbackInfo ci
   ) {
      VRenderSystem.modelView = (Matrix4f)modelViewMatrix;
      this.camX = cameraState.pos.x();
      this.camY = cameraState.pos.y();
      this.camZ = cameraState.pos.z();
   }

   @Redirect(
      method = "lambda$addMainPass$0",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"
      )
   )
   private void renderSectionLayer(ChunkSectionsToRender instance, ChunkSectionLayerGroup group, GpuSampler sampler) {
      if (group == ChunkSectionLayerGroup.OPAQUE) {
         Profiler profiler = Profiler.getMainProfiler();
         profiler.push("Opaque_terrain");
         this.worldRenderer.renderSectionLayer(TerrainRenderType.SOLID, this.camX, this.camY, this.camZ, VRenderSystem.modelView, VRenderSystem.projection);
         this.worldRenderer.renderSectionLayer(TerrainRenderType.CUTOUT, this.camX, this.camY, this.camZ, VRenderSystem.modelView, VRenderSystem.projection);
      } else if (group == ChunkSectionLayerGroup.TRANSLUCENT) {
         Profiler profiler = Profiler.getMainProfiler();
         profiler.pop();
         profiler.push("Translucent_terrain");
         this.worldRenderer
            .renderSectionLayer(TerrainRenderType.TRANSLUCENT, this.camX, this.camY, this.camZ, VRenderSystem.modelView, VRenderSystem.projection);
         profiler.pop();
      }
   }

   @Overwrite
   public void onChunkReadyToRender(ChunkPos chunkPos) {
   }

   @Overwrite
   private void setSectionDirty(int x, int y, int z, boolean flag) {
      this.worldRenderer.setSectionDirty(x, y, z, flag);
   }

   @Overwrite
   public String getSectionStatistics() {
      return this.worldRenderer.getChunkStatistics();
   }

   @Overwrite
   public boolean hasRenderedAllSections() {
      return !this.worldRenderer.graphNeedsUpdate() && this.worldRenderer.getTaskDispatcher().isIdle();
   }

   @Overwrite
   public int countRenderedSections() {
      return this.worldRenderer.getVisibleSectionsCount();
   }
}
