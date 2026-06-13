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
package com.xeno.mixin.compatibility;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import com.xeno.render.engine.VkGpuTexture;
import com.xeno.vulkan.Renderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
public abstract class PostPassM {
   @Shadow
   @Final
   private List<PostPass.Input> inputs;

   @Inject(
      method = "lambda$addToFrame$1",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;createCommandEncoder()Lcom/mojang/blaze3d/systems/CommandEncoder;")
   )
   private void transitionLayouts(ResourceHandle outputHandle, GpuBufferSlice shaderOrthoMatrix, Map targets, CallbackInfo ci) {
      Renderer.getInstance().endRenderPass();

      for (PostPass.Input input : this.inputs) {
         VkGpuTexture gpuTexture = (VkGpuTexture)input.texture(targets).texture();
         if (gpuTexture.needsClear()) {
            gpuTexture.getFbo(null).bind();
         }

         gpuTexture.getVulkanImage().readOnlyLayout();
      }
   }
}
