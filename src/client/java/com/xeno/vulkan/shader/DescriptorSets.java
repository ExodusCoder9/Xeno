package com.xeno.vulkan.shader;

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


import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.memory.buffer.BufferSlice;
import com.xeno.vulkan.memory.buffer.UniformBuffer;
import com.xeno.vulkan.shader.descriptor.ImageDescriptor;
import com.xeno.vulkan.shader.descriptor.UBO;
import com.xeno.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

public class DescriptorSets {
   private static final VkDevice DEVICE = Vulkan.getVkDevice();
   private final Pipeline pipeline;
   private int poolSize = 10;
   private long descriptorPool = 0L;
   private long[] sets;
   private long currentSet;
   private int currentIdx = -1;
   private final long[] boundUBs;
   private final ImageDescriptor.State[] boundTextures;
   private final IntBuffer dynamicOffsets;

   DescriptorSets(Pipeline pipeline) {
      this.pipeline = pipeline;
      this.boundTextures = new ImageDescriptor.State[pipeline.imageDescriptors.size()];
      this.dynamicOffsets = MemoryUtil.memAllocInt(pipeline.buffers.size());
      this.boundUBs = new long[pipeline.buffers.size()];
      Arrays.setAll(this.boundTextures, i -> new ImageDescriptor.State(0L, 0L));
      MemoryStack stack = MemoryStack.stackPush();

      try {
         this.createDescriptorPool(stack);
         this.createDescriptorSets(stack);
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

   public void bindSets(VkCommandBuffer commandBuffer, UniformBuffer uniformBuffer, int bindPoint) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         this.updateUniforms(uniformBuffer);
         this.updateDescriptorSet(stack, uniformBuffer);
         VK10.vkCmdBindDescriptorSets(commandBuffer, bindPoint, this.pipeline.pipelineLayout, 0, stack.longs(this.currentSet), this.dynamicOffsets);
      } catch (Throwable var8) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void updateUniforms(UniformBuffer globalUB) {
      int i = 0;

      for (UBO ubo : this.pipeline.getBuffers()) {
         if (ubo.getBufferSlice().getBuffer() == null) {
            ubo.setUseGlobalBuffer(true);
            ubo.setUpdate(true);
         }

         boolean useOwnUB = !ubo.useGlobalBuffer();
         int offset;
         if (useOwnUB) {
            BufferSlice bufferSlice = ubo.getBufferSlice();
            offset = (int)bufferSlice.getOffset();
         } else {
            offset = (int)globalUB.getUsedBytes();
            int alignedSize = UniformBuffer.getAlignedSize(ubo.getSize());
            globalUB.checkCapacity(alignedSize);
            if (ubo.shouldUpdate()) {
               ubo.update(globalUB.getPointer());
            }

            globalUB.updateOffset(alignedSize);
            BufferSlice bufferSlice = ubo.getBufferSlice();
            bufferSlice.set(globalUB, offset, alignedSize);
         }

         this.dynamicOffsets.put(i, offset);
         i++;
      }
   }

   private boolean needsUpdate(UniformBuffer uniformBuffer) {
      if (this.currentIdx == -1) {
         return true;
      }

      for (int j = 0; j < this.pipeline.imageDescriptors.size(); j++) {
         ImageDescriptor imageDescriptor = this.pipeline.imageDescriptors.get(j);
         VulkanImage image = imageDescriptor.getImage();
         if (image == null) {
            throw new NullPointerException(
               "Pipeline %s: image descriptor %s (binding %d) has no image bound"
                  .formatted(this.pipeline.name, imageDescriptor.name, imageDescriptor.getBinding())
            );
         }

         long view = imageDescriptor.getImageView(image);
         long sampler = image.getSampler();
         if (imageDescriptor.isReadOnlyLayout) {
            image.readOnlyLayout();
         }

         if (!this.boundTextures[j].isCurrentState(view, sampler)) {
            return true;
         }
      }

      for (int j = 0; j < this.pipeline.buffers.size(); j++) {
         UBO ubo = this.pipeline.buffers.get(j);
         Buffer uniformBufferI = ubo.getBufferSlice().getBuffer();
         if (uniformBufferI == null) {
            uniformBufferI = uniformBuffer;
         }

         if (this.boundUBs[j] != uniformBufferI.getId()) {
            return true;
         }
      }

      return false;
   }

   private void checkPoolSize(MemoryStack stack) {
      if (this.currentIdx >= this.poolSize) {
         this.poolSize *= 2;
         this.createDescriptorPool(stack);
         this.createDescriptorSets(stack);
         this.currentIdx = 0;
      }
   }

   private void updateDescriptorSet(MemoryStack stack, UniformBuffer uniformBuffer) {
      if (this.needsUpdate(uniformBuffer)) {
         this.currentIdx++;
         this.checkPoolSize(stack);
         this.currentSet = this.sets[this.currentIdx];
         org.lwjgl.vulkan.VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(
            this.pipeline.buffers.size() + this.pipeline.imageDescriptors.size(), stack
         );
         org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer[] bufferInfos = new org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer[this.pipeline.buffers.size()];
         int i = 0;

         for (UBO ubo : this.pipeline.getBuffers()) {
            Buffer ub = ubo.getBufferSlice().getBuffer();
            this.boundUBs[i] = ub.getId();
            bufferInfos[i] = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfos[i].buffer(this.boundUBs[i]);
            bufferInfos[i].range(ubo.getSize());
            VkWriteDescriptorSet descriptorWrite = (VkWriteDescriptorSet)descriptorWrites.get(i);
            descriptorWrite.sType$Default();
            descriptorWrite.dstBinding(ubo.getBinding());
            descriptorWrite.dstArrayElement(0);
            descriptorWrite.descriptorType(ubo.getType());
            descriptorWrite.descriptorCount(1);
            descriptorWrite.pBufferInfo(bufferInfos[i]);
            descriptorWrite.dstSet(this.currentSet);
            i++;
         }

         org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer[] imageInfo = new org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer[this.pipeline.imageDescriptors.size()];

         for (int j = 0; j < this.pipeline.imageDescriptors.size(); j++) {
            ImageDescriptor imageDescriptor = this.pipeline.imageDescriptors.get(j);
            VulkanImage image = imageDescriptor.getImage();
            if (image == null) {
               throw new NullPointerException(
                  "Pipeline %s: image descriptor %s (binding %d) has no image bound"
                     .formatted(this.pipeline.name, imageDescriptor.name, imageDescriptor.getBinding())
               );
            }

            long view = imageDescriptor.getImageView(image);
            long sampler = image.getSampler();
            int layout = imageDescriptor.getLayout();
            if (imageDescriptor.isReadOnlyLayout) {
               image.readOnlyLayout();
            }

            imageInfo[j] = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo[j].imageLayout(layout);
            imageInfo[j].imageView(view);
            if (imageDescriptor.useSampler) {
               imageInfo[j].sampler(sampler);
            }

            VkWriteDescriptorSet descriptorWrite = (VkWriteDescriptorSet)descriptorWrites.get(i);
            descriptorWrite.sType$Default();
            descriptorWrite.dstBinding(imageDescriptor.getBinding());
            descriptorWrite.dstArrayElement(0);
            descriptorWrite.descriptorType(imageDescriptor.getType());
            descriptorWrite.descriptorCount(1);
            descriptorWrite.pImageInfo(imageInfo[j]);
            descriptorWrite.dstSet(this.currentSet);
            this.boundTextures[j].set(view, sampler);
            i++;
         }

         VK10.vkUpdateDescriptorSets(DEVICE, descriptorWrites, null);
      }
   }

   private void createDescriptorSets(MemoryStack stack) {
      LongBuffer layouts = MemoryUtil.memAllocLong(this.poolSize);

      for (int i = 0; i < this.poolSize; i++) {
         layouts.put(i, this.pipeline.descriptorSetLayout);
      }

      VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack);
      allocInfo.sType$Default();
      allocInfo.descriptorPool(this.descriptorPool);
      allocInfo.pSetLayouts(layouts);
      this.sets = new long[this.poolSize];
      int result = VK10.vkAllocateDescriptorSets(DEVICE, allocInfo, this.sets);
      if (result != 0) {
         throw new RuntimeException("Failed to allocate descriptor sets. Result:" + result);
      }

      MemoryUtil.memFree(layouts);
   }

   private void createDescriptorPool(MemoryStack stack) {
      int size = this.pipeline.buffers.size() + this.pipeline.imageDescriptors.size();
      org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(size, stack);
      int i = 0;

      for (UBO buffer : this.pipeline.getBuffers()) {
         VkDescriptorPoolSize uniformBufferPoolSize = (VkDescriptorPoolSize)poolSizes.get(i);
         uniformBufferPoolSize.type(buffer.getType());
         uniformBufferPoolSize.descriptorCount(this.poolSize);
         i++;
      }

      for (ImageDescriptor imageDescriptor : this.pipeline.getImageDescriptors()) {
         VkDescriptorPoolSize textureSamplerPoolSize = (VkDescriptorPoolSize)poolSizes.get(i);
         textureSamplerPoolSize.type(imageDescriptor.getType());
         textureSamplerPoolSize.descriptorCount(this.poolSize);
         i++;
      }

      VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack);
      poolInfo.sType(33);
      poolInfo.pPoolSizes(poolSizes);
      poolInfo.maxSets(this.poolSize);
      LongBuffer pDescriptorPool = stack.mallocLong(1);
      if (VK10.vkCreateDescriptorPool(DEVICE, poolInfo, null, pDescriptorPool) != 0) {
         throw new RuntimeException("Failed to create descriptor pool");
      }

      if (this.descriptorPool != 0L) {
         long oldDescriptorPool = this.descriptorPool;
         MemoryManager.getInstance().addFrameOp(() -> VK10.vkDestroyDescriptorPool(DEVICE, oldDescriptorPool, null));
      }

      this.descriptorPool = pDescriptorPool.get(0);
   }

   public void resetIdx() {
      this.currentIdx = -1;
   }

   public void cleanUp() {
      VK10.vkResetDescriptorPool(DEVICE, this.descriptorPool, 0);
      VK10.vkDestroyDescriptorPool(DEVICE, this.descriptorPool, null);
      MemoryUtil.memFree(this.dynamicOffsets);
   }
}
