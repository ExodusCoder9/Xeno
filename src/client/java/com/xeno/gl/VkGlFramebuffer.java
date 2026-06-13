package com.xeno.gl;

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


import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.framebuffer.Framebuffer;
import com.xeno.vulkan.framebuffer.RenderPass;
import com.xeno.vulkan.texture.ImageUtil;
import com.xeno.vulkan.texture.VulkanImage;

public class VkGlFramebuffer {
   private static int idCounter = 1;
   private static final Int2ReferenceOpenHashMap<VkGlFramebuffer> map = new Int2ReferenceOpenHashMap();
   private static VkGlFramebuffer boundFramebuffer;
   private static VkGlFramebuffer readFramebuffer;
   public final int id;
   Framebuffer framebuffer;
   RenderPass renderPass;
   VulkanImage colorAttachment;
   VulkanImage depthAttachment;
   int level = 0;
   boolean needsUpdate;

   public static void resetBoundFramebuffer() {
      boundFramebuffer = null;
   }

   public static void beginRendering(VkGlFramebuffer glFramebuffer) {
      boolean begunRendering = glFramebuffer.beginRendering();
      if (begunRendering) {
         Framebuffer framebuffer = glFramebuffer.framebuffer;
         int viewWidth = framebuffer.getWidth();
         int viewHeight = framebuffer.getHeight();
         Renderer.setInvertedViewport(0, 0, viewWidth, viewHeight);
         Renderer.setScissor(0, 0, viewWidth, viewHeight);
         VRenderSystem.disableCull();
      }

      boundFramebuffer = glFramebuffer;
   }

   public static int genFramebufferId() {
      int id = idCounter;
      map.put(id, new VkGlFramebuffer(id));
      idCounter++;
      return id;
   }

   public static void bindFramebuffer(int target, int id) {
      if (id == 0) {
         Renderer.getInstance().endRenderPass();
         if (Renderer.isRecording()) {
            Renderer.getInstance().getMainPass().rebindMainTarget();
         }

         boundFramebuffer = null;
      } else {
         VkGlFramebuffer glFramebuffer = (VkGlFramebuffer)map.get(id);
         if (glFramebuffer == null) {
            throw new NullPointerException("No Framebuffer with ID: %d ".formatted(id));
         }

         if (glFramebuffer.needsUpdate) {
            glFramebuffer.create();
         }

         switch (target) {
            case 36008:
               readFramebuffer = glFramebuffer;
               break;
            case 36009:
            case 36160:
               if (glFramebuffer.framebuffer != null) {
                  beginRendering(glFramebuffer);
               }

               boundFramebuffer = glFramebuffer;
         }
      }
   }

   public static void deleteFramebuffer(int id) {
      if (id != 0) {
         boundFramebuffer = (VkGlFramebuffer)map.remove(id);
         if (boundFramebuffer == null) {
            throw new NullPointerException("bound framebuffer is null");
         }

         boundFramebuffer.cleanUp(true);
         boundFramebuffer = null;
      }
   }

   public static void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
      if (attachment != 36064 && attachment != 36096) {
         throw new UnsupportedOperationException();
      }

      if (texTarget != 3553) {
         throw new UnsupportedOperationException();
      }

      if (level != 0) {
         throw new UnsupportedOperationException();
      }

      boundFramebuffer.setAttachmentTexture(attachment, texture);
      boundFramebuffer.create();
      beginRendering(boundFramebuffer);
   }

   public static void framebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
      if (boundFramebuffer != null) {
         boundFramebuffer.setAttachmentRenderbuffer(attachment, renderbuffer);
         boundFramebuffer.create();
         beginRendering(boundFramebuffer);
      }
   }

   public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
      ImageUtil.blitFramebuffer(boundFramebuffer.colorAttachment, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1);
   }

   public static int glCheckFramebufferStatus(int target) {
      return 36053;
   }

   public static VkGlFramebuffer getBoundFramebuffer() {
      return boundFramebuffer;
   }

   public static VkGlFramebuffer getFramebuffer(int id) {
      return (VkGlFramebuffer)map.get(id);
   }

   VkGlFramebuffer(int i) {
      this.id = i;
   }

   boolean beginRendering() {
      return Renderer.getInstance().beginRenderPass(this.renderPass, this.framebuffer);
   }

   public void setAttachmentTexture(int attachment, int id) {
      VkGlTexture vkGlTexture = VkGlTexture.getTexture(id);
      if (vkGlTexture == null) {
         throw new NullPointerException(String.format("Texture %d is null", id));
      }

      this.setAttachmentImage(attachment, vkGlTexture.getVulkanImage());
   }

   public void setAttachmentRenderbuffer(int attachment, int id) {
      VkGlRenderbuffer renderbuffer = VkGlRenderbuffer.getRenderbuffer(id);
      if (renderbuffer == null) {
         throw new NullPointerException(String.format("Texture %d is null", id));
      }

      this.setAttachmentImage(attachment, renderbuffer.getVulkanImage());
   }

   public void setAttachmentImage(int attachment, VulkanImage image) {
      if (image == null) {
         throw new NullPointerException("Image is null");
      }

      switch (attachment) {
         case 36064:
            this.setColorAttachment(image);
            break;
         case 36096:
            this.setDepthAttachment(image);
            break;
         default:
            throw new IllegalStateException("Unexpected value: " + attachment);
      }

      this.needsUpdate = true;
   }

   public void setLevel(int level) {
      this.level = level;
      if (this.framebuffer != null) {
         this.framebuffer.setLevel(level);
      }
   }

   void setColorAttachment(VulkanImage image) {
      this.colorAttachment = image;
   }

   void setDepthAttachment(VulkanImage image) {
      this.depthAttachment = image;
   }

   public void create() {
      if (this.colorAttachment != null) {
         if (this.framebuffer != null) {
            this.cleanUp(false);
         }

         boolean hasDepthImage = this.depthAttachment != null;
         VulkanImage depthImage = this.depthAttachment;
         this.framebuffer = Framebuffer.builder(this.colorAttachment, depthImage).build();
         this.framebuffer.setLevel(this.level);
         RenderPass.Builder builder = RenderPass.builder(this.framebuffer);
         builder.getColorAttachmentInfo().setLoadOp(0).setFinalLayout(5);
         if (hasDepthImage) {
            builder.getDepthAttachmentInfo().setOps(0, 0);
         }

         this.renderPass = builder.build();
         this.needsUpdate = false;
      }
   }

   public Framebuffer getFramebuffer() {
      return this.framebuffer;
   }

   public RenderPass getRenderPass() {
      return this.renderPass;
   }

   void cleanUp(boolean freeAttachments) {
      if (this.framebuffer != null) {
         this.framebuffer.cleanUp(freeAttachments);
         this.renderPass.cleanUp();
      }

      this.framebuffer = null;
      this.renderPass = null;
   }
}
