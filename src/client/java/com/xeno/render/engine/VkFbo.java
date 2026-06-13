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

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.util.ARGB;
import com.xeno.gl.VkGlFramebuffer;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;

public class VkFbo {
   final int glId = GlStateManager.glGenFramebuffers();
   final VkTextureView colorAttachmentView;
   final VkGpuTexture depthAttachment;

   protected VkFbo(VkTextureView colorAttachmentView, VkGpuTexture depthAttachment) {
      this.colorAttachmentView = colorAttachmentView;
      this.depthAttachment = depthAttachment;
      VkGlFramebuffer fbo = VkGlFramebuffer.getFramebuffer(this.glId);
      VkGpuTexture colorAttachmentTexture = this.colorAttachmentView.texture();
      fbo.setAttachmentTexture(36064, colorAttachmentTexture.id);
      if (depthAttachment != null) {
         fbo.setAttachmentTexture(36096, depthAttachment.id);
      }

      fbo.setLevel(this.colorAttachmentView.baseMipLevel());
   }

   public void bind() {
      VkGlFramebuffer.bindFramebuffer(36160, this.glId);
      this.clearAttachments();
   }

   protected void clearAttachments() {
      int clear = 0;
      VkGpuTexture colorAttachmentTexture = this.colorAttachmentView.texture();
      if (colorAttachmentTexture.needsClear()) {
         clear |= 16384;
         int clearColor = colorAttachmentTexture.clearColor;
         VRenderSystem.setClearColor(ARGB.redFloat(clearColor), ARGB.greenFloat(clearColor), ARGB.blueFloat(clearColor), ARGB.alphaFloat(clearColor));
         colorAttachmentTexture.needsClear = false;
      }

      if (this.depthAttachment != null && this.depthAttachment.needsClear()) {
         clear |= 256;
         float clearDepth = this.depthAttachment.depthClearValue;
         VRenderSystem.clearDepth(clearDepth);
         this.depthAttachment.needsClear = false;
      }

      if (clear != 0) {
         Renderer.clearAttachments(clear);
      }
   }

   protected void close() {
      VkGlFramebuffer.deleteFramebuffer(this.glId);
   }

   public boolean needsClear() {
      return this.colorAttachmentView.texture().needsClear() || this.depthAttachment != null && this.depthAttachment.needsClear();
   }
}
