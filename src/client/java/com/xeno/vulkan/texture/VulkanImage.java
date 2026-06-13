package com.xeno.vulkan.texture;

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


import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import com.xeno.render.texture.ImageUploadHelper;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.memory.buffer.StagingBuffer;
import com.xeno.vulkan.queue.CommandPool;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer;

public class VulkanImage {
   public static int DefaultFormat = 37;
   private static final VkDevice DEVICE = Vulkan.getVkDevice();
   public final String name;
   public final int format;
   public final int aspect;
   public final int arrayLayers;
   public final int mipLevels;
   public final int width;
   public final int height;
   public final int formatSize;
   public final int usage;
   public final int viewType;
   public final int size;
   private long id;
   private long allocation;
   private long mainImageView;
   private final Int2LongArrayMap imageViews = new Int2LongArrayMap(4);
   private final long[] levelImageViews;
   private long sampler;
   private int currentLayout;

   public VulkanImage(String name, long id, int format, int mipLevels, int width, int height, int formatSize, int usage, long imageView) {
      this.id = id;
      this.mainImageView = imageView;
      this.name = name;
      this.arrayLayers = 1;
      this.mipLevels = mipLevels;
      this.width = width;
      this.height = height;
      this.formatSize = formatSize;
      this.format = format;
      this.usage = usage;
      this.aspect = getAspect(this.format);
      this.viewType = 1;
      this.size = width * height * formatSize;
      this.levelImageViews = new long[mipLevels];
      this.sampler = SamplerManager.getDefaultSampler();
   }

   private VulkanImage(VulkanImage.Builder builder) {
      this.name = builder.name;
      this.mipLevels = builder.mipLevels;
      this.width = builder.width;
      this.height = builder.height;
      this.arrayLayers = builder.arrayLayers;
      this.formatSize = builder.formatSize;
      this.format = builder.format;
      this.usage = builder.usage;
      this.aspect = getAspect(this.format);
      this.viewType = builder.viewType;
      this.size = this.width * this.height * this.formatSize;
      this.levelImageViews = new long[builder.mipLevels];
   }

   public static VulkanImage createTextureImage(VulkanImage.Builder builder) {
      VulkanImage image = new VulkanImage(builder);
      image.createImage();
      image.mainImageView = createImageView(image.id, image.viewType, image.format, image.aspect, image.arrayLayers, 0, image.mipLevels);
      image.sampler = SamplerManager.getSampler(builder.clamp, builder.linearFiltering, builder.mipLevels - 1);
      return image;
   }

   public static VulkanImage createDepthImage(int format, int width, int height, int usage, boolean blur, boolean clamp) {
      return builder(width, height).setFormat(format).setUsage(usage).setLinearFiltering(blur).setClamp(clamp).createVulkanImage();
   }

   public static VulkanImage createWhiteTexture() {
      MemoryStack stack = MemoryStack.stackPush();

      VulkanImage var4;
      try {
         int i = -1;
         ByteBuffer buffer = stack.malloc(4);
         buffer.putInt(0, i);
         VulkanImage image = builder(1, 1).setFormat(DefaultFormat).setUsage(6).setLinearFiltering(false).setClamp(false).createVulkanImage();
         image.uploadSubTextureAsync(0, 0, image.width, image.height, 0, 0, 0, 0, image.width, buffer);
         var4 = image;
      } catch (Throwable var6) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (stack != null) {
         stack.close();
      }

      return var4;
   }

   private void createImage() {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         LongBuffer pTextureImage = stack.mallocLong(1);
         PointerBuffer pAllocation = stack.pointers(0L);
         int flags = this.viewType == 3 ? 16 : 0;
         MemoryManager.getInstance()
            .createImage(this.width, this.height, this.arrayLayers, this.mipLevels, this.format, 0, this.usage, flags, 1, pTextureImage, pAllocation);
         this.id = pTextureImage.get(0);
         this.allocation = pAllocation.get(0);
         MemoryManager.addImage(this);
         if (this.name != null) {
            Vulkan.setDebugLabel(stack, 10, pTextureImage.get(), this.name);
         }
      } catch (Throwable var6) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (stack != null) {
         stack.close();
      }
   }

   public static int getAspect(int format) {
      return switch (format) {
         case 124, 125, 126 -> 2;
         default -> 1;
         case 129, 130 -> 6;
      };
   }

   public static boolean isDepthFormat(int format) {
      return switch (format) {
         case 124, 125, 126, 129, 130 -> true;
         default -> false;
      };
   }

   public static long createImageView(long image, int format, int aspectFlags, int arrayLayers, int mipLevels) {
      return createImageView(image, 1, format, aspectFlags, arrayLayers, 0, mipLevels);
   }

   public static long createImageView(long image, int viewType, int format, int aspectFlags, int arrayLayers, int baseMipLevel, int mipLevels) {
      MemoryStack stack = MemoryStack.stackPush();

      long var11;
      try {
         VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack);
         viewInfo.sType(15);
         viewInfo.image(image);
         viewInfo.viewType(viewType);
         viewInfo.format(format);
         viewInfo.subresourceRange().aspectMask(aspectFlags);
         viewInfo.subresourceRange().baseMipLevel(baseMipLevel);
         viewInfo.subresourceRange().levelCount(mipLevels);
         viewInfo.subresourceRange().baseArrayLayer(0);
         viewInfo.subresourceRange().layerCount(arrayLayers);
         LongBuffer pImageView = stack.mallocLong(1);
         if (VK10.vkCreateImageView(DEVICE, viewInfo, null, pImageView) != 0) {
            throw new RuntimeException("Failed to create texture image view");
         }

         var11 = pImageView.get(0);
      } catch (Throwable var14) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }
         }

         throw var14;
      }

      if (stack != null) {
         stack.close();
      }

      return var11;
   }

   public void uploadSubTextureAsync(
      int mipLevel, int width, int height, int xOffset, int yOffset, int unpackSkipRows, int unpackSkipPixels, int unpackRowLength, ByteBuffer buffer
   ) {
      this.uploadSubTextureAsync(mipLevel, 0, width, height, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, unpackRowLength, MemoryUtil.memAddress(buffer));
   }

   public void uploadSubTextureAsync(
      int mipLevel,
      int arrayLayer,
      int width,
      int height,
      int xOffset,
      int yOffset,
      int unpackSkipRows,
      int unpackSkipPixels,
      int unpackRowLength,
      ByteBuffer buffer
   ) {
      this.uploadSubTextureAsync(
         mipLevel, arrayLayer, width, height, xOffset, yOffset, unpackSkipRows, unpackSkipPixels, unpackRowLength, MemoryUtil.memAddress(buffer)
      );
   }

   public void uploadSubTextureAsync(
      int mipLevel, int arrayLayer, int width, int height, int xOffset, int yOffset, int unpackSkipRows, int unpackSkipPixels, int unpackRowLength, long srcPtr
   ) {
      long uploadSize = (long)(unpackRowLength * height - unpackSkipPixels) * this.formatSize;
      StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
      if (uploadSize > stagingBuffer.getBufferSize()) {
         stagingBuffer = new StagingBuffer(uploadSize);
         stagingBuffer.scheduleFree();
      }

      srcPtr += ((long)unpackRowLength * unpackSkipRows + unpackSkipPixels) * this.formatSize;
      stagingBuffer.align(this.formatSize);
      stagingBuffer.copyBuffer((int)uploadSize, srcPtr);
      long bufferId = stagingBuffer.getId();
      VkCommandBuffer commandBuffer = ImageUploadHelper.INSTANCE.getOrStartCommandBuffer().getHandle();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         this.transferDstLayout(stack, commandBuffer);
         int srcOffset = (int)stagingBuffer.getOffset();
         ImageUtil.copyBufferToImageCmd(
            stack, commandBuffer, bufferId, this.id, arrayLayer, mipLevel, width, height, xOffset, yOffset, srcOffset, unpackRowLength, height
         );
         ImageUtil.imageTransferMemoryBarrier(stack, commandBuffer, this, mipLevel);
      } catch (Throwable var22) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var21) {
               var22.addSuppressed(var21);
            }
         }

         throw var22;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void transferDstLayout(MemoryStack stack, VkCommandBuffer commandBuffer) {
      this.transitionImageLayout(stack, commandBuffer, 7);
   }

   public void readOnlyLayout() {
      if (this.currentLayout != 5) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            if (Renderer.getInstance().getBoundRenderPass() != null) {
               CommandPool.CommandBuffer commandBuffer = ImageUploadHelper.INSTANCE.getOrStartCommandBuffer();
               VkCommandBuffer vkCommandBuffer = commandBuffer.getHandle();
               this.readOnlyLayout(stack, vkCommandBuffer);
            } else {
               this.readOnlyLayout(stack, Renderer.getCommandBuffer());
            }
         } catch (Throwable var5) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (stack != null) {
            stack.close();
         }
      }
   }

   public void readOnlyLayout(MemoryStack stack, VkCommandBuffer commandBuffer) {
      this.transitionImageLayout(stack, commandBuffer, 5);
   }

   public void setSampler(long sampler) {
      this.sampler = sampler;
   }

   public void transitionImageLayout(MemoryStack stack, VkCommandBuffer commandBuffer, int newLayout) {
      transitionImageLayout(stack, commandBuffer, this, newLayout);
   }

   public static void transitionImageLayout(MemoryStack stack, VkCommandBuffer commandBuffer, VulkanImage image, int newLayout) {
      if (image.currentLayout != newLayout) {
         int dstAccessMask = 0;
         int sourceStage;
         int srcAccessMask;
         switch (image.currentLayout) {
            case 0:
            case 1000001002:
               srcAccessMask = 0;
               sourceStage = 8192;
               break;
            case 2:
               srcAccessMask = 256;
               sourceStage = 1024;
               break;
            case 3:
               srcAccessMask = 1536;
               sourceStage = 512;
               break;
            case 5:
               srcAccessMask = 32;
               sourceStage = 128;
               break;
            case 6:
               srcAccessMask = 2048;
               sourceStage = 4096;
               break;
            case 7:
               srcAccessMask = 4096;
               sourceStage = 4096;
               break;
            default:
               throw new RuntimeException("Unexpected value:" + image.currentLayout);
         }
         transitionLayout(stack, commandBuffer, image, image.currentLayout, newLayout, sourceStage, srcAccessMask, switch (newLayout) {
            case 2 -> {
               dstAccessMask = 384;
               yield 1024;
            }
            case 3 -> {
               dstAccessMask = 1536;
               yield 256;
            }
            case 5 -> {
               dstAccessMask = 32;
               yield 136;
            }
            case 6 -> {
               dstAccessMask = 2048;
               yield 4096;
            }
            case 7 -> {
               dstAccessMask = 4096;
               yield 4096;
            }
            case 1000001002 -> 8192;
            default -> throw new RuntimeException("Unexpected value:" + newLayout);
         }, dstAccessMask);
      }
   }

   public static void transitionLayout(
      MemoryStack stack,
      VkCommandBuffer commandBuffer,
      VulkanImage image,
      int oldLayout,
      int newLayout,
      int sourceStage,
      int srcAccessMask,
      int destinationStage,
      int dstAccessMask
   ) {
      transitionLayout(stack, commandBuffer, image, 0, oldLayout, newLayout, sourceStage, srcAccessMask, destinationStage, dstAccessMask);
   }

   public static void transitionLayout(
      MemoryStack stack,
      VkCommandBuffer commandBuffer,
      VulkanImage image,
      int baseLevel,
      int oldLayout,
      int newLayout,
      int sourceStage,
      int srcAccessMask,
      int destinationStage,
      int dstAccessMask
   ) {
      Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
      barrier.sType(45);
      barrier.oldLayout(image.currentLayout);
      barrier.newLayout(newLayout);
      barrier.srcQueueFamilyIndex(-1);
      barrier.dstQueueFamilyIndex(-1);
      barrier.image(image.getId());
      barrier.subresourceRange().baseMipLevel(baseLevel);
      barrier.subresourceRange().levelCount(-1);
      barrier.subresourceRange().baseArrayLayer(0);
      barrier.subresourceRange().layerCount(-1);
      barrier.subresourceRange().aspectMask(image.aspect);
      barrier.srcAccessMask(srcAccessMask);
      barrier.dstAccessMask(dstAccessMask);
      VK10.vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0, null, null, barrier);
      image.currentLayout = newLayout;
   }

   private static boolean hasStencilComponent(int format) {
      return format == 130 || format == 129;
   }

   public void free() {
      MemoryManager.getInstance().addToFreeable(this);
   }

   public void doFree() {
      if (this.id != 0L) {
         MemoryManager.freeImage(this.id, this.allocation);
         VK10.vkDestroyImageView(Vulkan.getVkDevice(), this.mainImageView, null);
         if (this.levelImageViews != null) {
            Arrays.stream(this.levelImageViews).forEach(imageView -> {
               if (imageView != 0L) {
                  VK10.vkDestroyImageView(Vulkan.getVkDevice(), imageView, null);
               }
            });
         }

         this.id = 0L;
      }
   }

   public int getCurrentLayout() {
      return this.currentLayout;
   }

   public void setCurrentLayout(int currentLayout) {
      this.currentLayout = currentLayout;
   }

   public long getId() {
      return this.id;
   }

   public long getAllocation() {
      return this.allocation;
   }

   public long getImageView() {
      return this.mainImageView;
   }

   public long getImageView(int format) {
      if (this.format == format) {
         return this.mainImageView;
      }

      long imageView = this.imageViews.get(format);
      if (imageView == 0L) {
         imageView = createImageView(this.id, 1, format, this.aspect, this.arrayLayers, 0, this.mipLevels);
         this.imageViews.put(format, imageView);
      }

      return imageView;
   }

   public long getLevelImageView(int i) {
      if (this.levelImageViews[i] == 0L) {
         this.levelImageViews[i] = createImageView(this.id, 1, this.format, this.aspect, this.arrayLayers, i, 1);
      }

      return this.levelImageViews[i];
   }

   public long[] getLevelImageViews() {
      return this.levelImageViews;
   }

   public long getSampler() {
      return this.sampler;
   }

   public static VulkanImage.Builder builder(int width, int height) {
      return new VulkanImage.Builder(width, height);
   }

   public static class Builder {
      final int width;
      final int height;
      String name;
      int format = VulkanImage.DefaultFormat;
      int formatSize;
      int arrayLayers = 1;
      byte mipLevels = 1;
      int usage = 7;
      int viewType = 1;
      boolean linearFiltering = false;
      boolean clamp = false;
      int reductionMode = -1;

      public Builder(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public VulkanImage.Builder setName(String name) {
         this.name = name;
         return this;
      }

      public VulkanImage.Builder setFormat(int format) {
         this.format = format;
         return this;
      }

      public VulkanImage.Builder setArrayLayers(int n) {
         this.arrayLayers = (byte)n;
         return this;
      }

      public VulkanImage.Builder setMipLevels(int n) {
         this.mipLevels = (byte)n;
         return this;
      }

      public VulkanImage.Builder setUsage(int usage) {
         this.usage = usage;
         return this;
      }

      public VulkanImage.Builder addUsage(int usage) {
         this.usage |= usage;
         return this;
      }

      public VulkanImage.Builder setViewType(int viewType) {
         this.viewType = viewType;
         return this;
      }

      public VulkanImage.Builder setLinearFiltering(boolean b) {
         this.linearFiltering = b;
         return this;
      }

      public VulkanImage.Builder setClamp(boolean b) {
         this.clamp = b;
         return this;
      }

      public VulkanImage.Builder setSamplerReductionMode(int reductionMode) {
         this.reductionMode = reductionMode;
         return this;
      }

      public VulkanImage createVulkanImage() {
         this.formatSize = formatSize(this.format);
         return VulkanImage.createTextureImage(this);
      }

      private static int formatSize(int format) {
         return switch (format) {
            case 9 -> 1;
            case 37, 41, 42, 43, 100, 126, 129 -> 4;
            case 76 -> 2;
            case 97 -> 8;
            default -> throw new IllegalArgumentException(String.format("Unxepcted format: %s", format));
         };
      }
   }
}
