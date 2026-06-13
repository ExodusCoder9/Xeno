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

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import net.minecraft.client.renderer.DynamicUniforms;
import com.xeno.vulkan.Renderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
   @Shadow
   @Nullable
   private static Thread renderThread;
   @Shadow
   @Nullable
   private static GpuDevice DEVICE;
   @Shadow
   @Nullable
   private static DynamicUniforms dynamicUniforms;
   @Shadow
   private static SamplerCache samplerCache;
   @Shadow
   private static String apiDescription;

   @Overwrite
   public static void initRenderer(GpuDevice device) {
      if (DEVICE != null) {
         throw new IllegalStateException("RenderSystem.DEVICE already initialized");
      }

      DEVICE = device;
      Renderer.initRenderer();
      apiDescription = RenderSystem.getDevice().getImplementationInformation();
      dynamicUniforms = new DynamicUniforms();
      samplerCache.initialize();
   }
}
