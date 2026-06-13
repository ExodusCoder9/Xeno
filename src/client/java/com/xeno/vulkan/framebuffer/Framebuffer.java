package com.xeno.vulkan.framebuffer;

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


import it.unimi.dsi.fastutil.objects.Reference2LongArrayMap;
import java.nio.LongBuffer;
import java.util.Arrays;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.texture.VulkanImage;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkViewport.Buffer;

public class Framebuffer {
   public static final int DEFAULT_FORMAT = 37;
   public final String name;
   protected int format;
   protected int depthFormat;
   protected int width;
   protected int height;
   protected boolean linearFiltering;
   protected boolean depthLinearFiltering;
   protected int attachmentCount;
   boolean hasColorAttachment;
   boolean hasDepthAttachment;
   private VulkanImage colorAttachment;
   protected VulkanImage depthAttachment;
   private int level;
   private final Reference2LongArrayMap<RenderPass> renderpassToFramebufferMap = new Reference2LongArrayMap();

   protected Framebuffer() {
      this.name = null;
   }

   public Framebuffer(Framebuffer.Builder builder) {
      this.name = builder.name;
      this.format = builder.format;
      this.depthFormat = builder.depthFormat;
      this.width = builder.width;
      this.height = builder.height;
      this.linearFiltering = builder.linearFiltering;
      this.depthLinearFiltering = builder.depthLinearFiltering;
      this.hasColorAttachment = builder.hasColorAttachment;
      this.hasDepthAttachment = builder.hasDepthAttachment;
      if (builder.createImages) {
         this.createImages();
      } else {
         this.colorAttachment = builder.colorAttachment;
         this.depthAttachment = builder.depthAttachment;
      }

      this.level = builder.level;
   }

   public void createImages() {
      if (this.hasColorAttachment) {
         this.colorAttachment = VulkanImage.builder(this.width, this.height)
            .setName(this.name != null ? String.format("%s Color", this.name) : null)
            .setFormat(this.format)
            .setUsage(23)
            .setLinearFiltering(this.linearFiltering)
            .setClamp(true)
            .createVulkanImage();
      }

      if (this.hasDepthAttachment) {
         this.depthAttachment = VulkanImage.builder(this.width, this.height)
            .setName(this.name != null ? String.format("%s Depth", this.name) : null)
            .setFormat(this.depthFormat)
            .setUsage(39)
            .setLinearFiltering(this.depthLinearFiltering)
            .setClamp(true)
            .createVulkanImage();
         this.attachmentCount++;
      }
   }

   public void resize(int newWidth, int newHeight) {
      this.width = newWidth;
      this.height = newHeight;
      this.cleanUp();
      this.createImages();
   }

   private long createFramebuffer(RenderPass renderPass) {
      MemoryStack stack = MemoryStack.stackPush();

      long var6;
      try {
         LongBuffer attachments;
         if (this.colorAttachment != null && this.depthAttachment != null) {
            attachments = stack.longs(this.colorAttachment.getImageView(), this.depthAttachment.getImageView());
         } else {
            if (this.colorAttachment == null) {
               throw new IllegalStateException();
            }

            attachments = stack.longs(this.colorAttachment.getImageView());
         }

         LongBuffer pFramebuffer = stack.mallocLong(1);
         VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
         framebufferInfo.sType$Default();
         framebufferInfo.renderPass(renderPass.getId());
         framebufferInfo.width(this.width);
         framebufferInfo.height(this.height);
         framebufferInfo.layers(1);
         framebufferInfo.pAttachments(attachments);
         if (VK10.vkCreateFramebuffer(Vulkan.getVkDevice(), framebufferInfo, null, pFramebuffer) != 0) {
            throw new RuntimeException("Failed to create framebuffer");
         }

         var6 = pFramebuffer.get(0);
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
      }

      return var6;
   }

   public void beginRenderPass(VkCommandBuffer commandBuffer, RenderPass renderPass, MemoryStack stack) {
      renderPass.setFramebuffer(this);
      renderPass.beginDynamicRendering(commandBuffer, stack);
   }

   protected long getFramebufferId(RenderPass renderPass) {
      return this.renderpassToFramebufferMap.computeIfAbsent(renderPass, renderPass1 -> this.createFramebuffer(renderPass));
   }

   public Buffer viewport(MemoryStack stack) {
      Buffer viewport = VkViewport.malloc(1, stack);
      viewport.x(0.0F);
      viewport.y(this.height);
      viewport.width(this.width);
      viewport.height(-this.height);
      viewport.minDepth(0.0F);
      viewport.maxDepth(1.0F);
      return viewport;
   }

   public org.lwjgl.vulkan.VkRect2D.Buffer scissor(MemoryStack stack) {
      org.lwjgl.vulkan.VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack);
      scissor.offset().set(0, 0);
      scissor.extent().set(this.width, this.height);
      return scissor;
   }

   public void cleanUp() {
      this.cleanUp(true);
   }

   public void cleanUp(boolean cleanImages) {
      if (cleanImages) {
         if (this.colorAttachment != null) {
            this.colorAttachment.free();
         }

         if (this.depthAttachment != null) {
            this.depthAttachment.free();
         }
      }

      VkDevice device = Vulkan.getVkDevice();
      long[] ids = this.renderpassToFramebufferMap.values().toLongArray();
      MemoryManager.getInstance().addFrameOp(() -> Arrays.stream(ids).forEach(id -> VK10.vkDestroyFramebuffer(device, id, null)));
      this.renderpassToFramebufferMap.clear();
   }

   public void setLevel(int level) {
      int maxLevel = this.colorAttachment.mipLevels - 1;
      if (level > maxLevel) {
         throw new IllegalStateException("Requested mip level (%d) greater than color attachments max mip level (%d)".formatted(level, maxLevel));
      }

      this.level = level;
   }

   public long getDepthImageView() {
      return this.depthAttachment.getImageView();
   }

   public VulkanImage getDepthAttachment() {
      return this.depthAttachment;
   }

   public VulkanImage getColorAttachment() {
      return this.colorAttachment;
   }

   public long getColorAttachmentView() {
      return this.colorAttachment.getLevelImageView(this.level);
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public int getFormat() {
      return this.format;
   }

   public int getDepthFormat() {
      return this.depthFormat;
   }

   public static Framebuffer.Builder builder(int width, int height, int colorAttachments, boolean hasDepthAttachment) {
      return new Framebuffer.Builder(width, height, colorAttachments, hasDepthAttachment);
   }

   public static Framebuffer.Builder builder(VulkanImage colorAttachment, VulkanImage depthAttachment) {
      return new Framebuffer.Builder(colorAttachment, depthAttachment);
   }

   public static class Builder {
      final String name;
      final boolean createImages;
      final int width;
      final int height;
      int format;
      int depthFormat;
      VulkanImage colorAttachment;
      VulkanImage depthAttachment;
      boolean hasColorAttachment;
      boolean hasDepthAttachment;
      boolean linearFiltering;
      boolean depthLinearFiltering;
      int level = 0;

      public Builder(int width, int height, int colorAttachments, boolean hasDepthAttachment) {
         this(null, width, height, colorAttachments, hasDepthAttachment);
      }

      public Builder(String name, int width, int height, int colorAttachments, boolean hasDepthAttachment) {
         Validate.isTrue(colorAttachments > 0 || hasDepthAttachment, "At least 1 attachment needed", new Object[0]);
         Validate.isTrue(colorAttachments <= 1, "Not supported", new Object[0]);
         this.name = name;
         this.createImages = true;
         this.format = 37;
         this.depthFormat = Vulkan.getDefaultDepthFormat();
         this.linearFiltering = true;
         this.depthLinearFiltering = false;
         this.width = width;
         this.height = height;
         this.hasColorAttachment = colorAttachments == 1;
         this.hasDepthAttachment = hasDepthAttachment;
      }

      public Builder(VulkanImage colorAttachment, VulkanImage depthAttachment) {
         this.name = null;
         this.createImages = false;
         this.colorAttachment = colorAttachment;
         this.depthAttachment = depthAttachment;
         this.format = colorAttachment.format;
         this.width = colorAttachment.width;
         this.height = colorAttachment.height;
         this.hasColorAttachment = true;
         this.hasDepthAttachment = depthAttachment != null;
         this.depthFormat = this.hasDepthAttachment ? depthAttachment.format : 0;
         this.linearFiltering = true;
         this.depthLinearFiltering = false;
      }

      public Framebuffer build() {
         return new Framebuffer(this);
      }

      public Framebuffer.Builder setLevel(int level) {
         this.level = level;
         return this;
      }

      public Framebuffer.Builder setFormat(int format) {
         this.format = format;
         return this;
      }

      public Framebuffer.Builder setLinearFiltering(boolean b) {
         this.linearFiltering = b;
         return this;
      }

      public Framebuffer.Builder setDepthLinearFiltering(boolean b) {
         this.depthLinearFiltering = b;
         return this;
      }
   }
}
