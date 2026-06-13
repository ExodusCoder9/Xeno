package com.xeno.vulkan.pass;

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


import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.xeno.vulkan.framebuffer.Framebuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

public interface MainPass {
   void begin(VkCommandBuffer var1, MemoryStack var2);

   void end(VkCommandBuffer var1);

   void cleanUp();

   void onResize();

   default void mainTargetBindWrite() {
   }

   default void mainTargetUnbindWrite() {
   }

   default void rebindMainTarget() {
   }

   default void bindAsTexture() {
   }

   default Framebuffer getMainFramebuffer() {
      return null;
   }

   default GpuTexture getColorAttachment() {
      return null;
   }

   default GpuTextureView getColorAttachmentView() {
      return null;
   }

   default GpuTexture getDepthAttachment() {
      return null;
   }
}
