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

import com.mojang.blaze3d.systems.GpuBackend;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.main.GameConfig;
import com.xeno.Initializer;
import com.xeno.render.engine.VkBackend;
import com.xeno.vulkan.Vulkan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
   @Shadow
   @Final
   public Options options;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void forceGraphicsMode(GameConfig gameConfig, CallbackInfo ci) {
      OptionInstance<GraphicsPreset> graphicsModeOption = this.options.graphicsPreset();
      if (graphicsModeOption.get() == GraphicsPreset.FABULOUS) {
         Initializer.LOGGER.error("Fabulous graphics mode not supported, forcing Fancy.");
         graphicsModeOption.set(GraphicsPreset.FANCY);
      }

      if (this.options.improvedTransparency().get()) {
         Initializer.LOGGER.error("Improved transparency currently not supported, forcing it off.");
         this.options.improvedTransparency().set(false);
      }
   }

   @ModifyVariable(method = "<init>", at = @At("STORE"), name = "backends")
   private GpuBackend[] useVulkanBackend(GpuBackend[] backends) {
      return new GpuBackend[]{new VkBackend()};
   }

   @Inject(method = "close", at = @At("HEAD"))
   public void close(CallbackInfo ci) {
      Vulkan.waitIdle();
   }

   @Inject(method = "close", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;close()V"))
   public void close2(CallbackInfo ci) {
      Vulkan.cleanUp();
   }
}
