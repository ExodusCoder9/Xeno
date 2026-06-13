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
package com.xeno.mixin.render.clouds;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import com.xeno.render.profiling.Profiler;
import com.xeno.render.sky.CloudRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererM {
   @Shadow
   private int ticks;
   @Shadow
   @Nullable
   private ClientLevel level;
   @Shadow
   @Final
   private LevelTargetBundle targets;
   @Unique
   private CloudRenderer cloudRenderer;

   @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
   public void addCloudsPass(
      FrameGraphBuilder frame,
      CloudStatus cloudStatus,
      Vec3 cameraPosition,
      long gameTime,
      float partialTicks,
      int cloudColor,
      float cloudHeight,
      int cloudRange,
      CallbackInfo ci
   ) {
      if (this.cloudRenderer == null) {
         this.cloudRenderer = new CloudRenderer();
      }

      FramePass framePass = frame.addPass("clouds");
      if (this.targets.clouds != null) {
         this.targets.clouds = framePass.readsAndWrites(this.targets.clouds);
      } else {
         this.targets.main = framePass.readsAndWrites(this.targets.main);
      }

      framePass.executes(() -> {
         Profiler profiler = Profiler.getMainProfiler();
         profiler.push("Clouds");
         this.cloudRenderer.renderClouds(cloudHeight, cloudColor, cameraPosition.x(), cameraPosition.y(), cameraPosition.z(), gameTime, partialTicks);
         profiler.pop();
      });
      ci.cancel();
   }

   @Inject(method = "allChanged", at = @At("RETURN"))
   private void onAllChanged(CallbackInfo ci) {
      if (this.cloudRenderer != null) {
         this.cloudRenderer.resetBuffer();
      }
   }

   @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
   private void onReload(ResourceManager resourceManager, CallbackInfo ci) {
      if (this.cloudRenderer != null) {
         this.cloudRenderer.loadTexture();
      }
   }
}
