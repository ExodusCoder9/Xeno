package com.xeno.vulkan.util;

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


import com.xeno.render.shader.PipelineManager;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.shader.GraphicsPipeline;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;

public class DrawUtil {
   public DrawUtil() {
   }

   public static void blitToScreen() {
      fastBlit();
   }

   public static void fastBlit() {
      GraphicsPipeline blitPipeline = PipelineManager.getFastBlitPipeline();
      VRenderSystem.disableCull();
      VRenderSystem.setPrimitiveTopologyGL(4);
      Renderer renderer = Renderer.getInstance();
      renderer.bindGraphicsPipeline(blitPipeline);
      renderer.uploadAndBindUBOs(blitPipeline);
      VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
      VK11.vkCmdDraw(commandBuffer, 3, 1, 0, 0);
      VRenderSystem.enableCull();
   }
}
