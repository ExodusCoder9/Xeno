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
package com.xeno.vulkan.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.framebuffer.RenderPass;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.memory.buffer.UniformBuffer;
import com.xeno.vulkan.shader.descriptor.ImageDescriptor;
import com.xeno.vulkan.shader.descriptor.UBO;
import com.xeno.vulkan.shader.layout.AlignedStruct;
import com.xeno.vulkan.shader.layout.PushConstants;
import com.xeno.vulkan.shader.layout.Uniform;
import com.xeno.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer;

public abstract class Pipeline {
   private static final VkDevice DEVICE = Vulkan.getVkDevice();
   protected static final long PIPELINE_CACHE = createPipelineCache();
   protected static final List<Pipeline> PIPELINES = new ArrayList<>();
   public final String name;
   protected long descriptorSetLayout;
   protected long pipelineLayout;
   protected DescriptorSets[] descriptorSets;
   protected List<UBO> buffers;
   protected List<ImageDescriptor> imageDescriptors;
   protected PushConstants pushConstants;
   private final Map<String, UBO> uboNameMap = new HashMap<>();
   private final Map<Integer, UBO> uboBindingMap = new HashMap<>();
   private boolean mapsBuilt = false;

   private static long createPipelineCache() {
      MemoryStack stack = MemoryStack.stackPush();

      long var3;
      try {
         VkPipelineCacheCreateInfo cacheCreateInfo = VkPipelineCacheCreateInfo.calloc(stack);
         cacheCreateInfo.sType(17);
         LongBuffer pPipelineCache = stack.mallocLong(1);
         if (VK10.vkCreatePipelineCache(DEVICE, cacheCreateInfo, null, pPipelineCache) != 0) {
            throw new RuntimeException("Failed to create graphics pipeline");
         }

         var3 = pPipelineCache.get(0);
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

      return var3;
   }

   public static void destroyPipelineCache() {
      VK10.vkDestroyPipelineCache(DEVICE, PIPELINE_CACHE, null);
   }

   public static void recreateDescriptorSets(int frames) {
      PIPELINES.forEach(pipeline -> {
         pipeline.destroyDescriptorSets();
         pipeline.createDescriptorSets(frames);
      });
   }

   public Pipeline(String name) {
      this.name = name;
   }

   protected void createDescriptorSetLayout() {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         int bindingsSize = this.buffers.size() + this.imageDescriptors.size();
         Buffer bindings = VkDescriptorSetLayoutBinding.calloc(bindingsSize, stack);

         for (UBO ubo : this.buffers) {
            VkDescriptorSetLayoutBinding uboLayoutBinding = (VkDescriptorSetLayoutBinding)bindings.get(ubo.getBinding());
            uboLayoutBinding.binding(ubo.getBinding());
            uboLayoutBinding.descriptorCount(1);
            uboLayoutBinding.descriptorType(ubo.getType());
            uboLayoutBinding.pImmutableSamplers(null);
            uboLayoutBinding.stageFlags(ubo.getStages());
         }

         for (ImageDescriptor imageDescriptor : this.imageDescriptors) {
            VkDescriptorSetLayoutBinding samplerLayoutBinding = (VkDescriptorSetLayoutBinding)bindings.get(imageDescriptor.getBinding());
            samplerLayoutBinding.binding(imageDescriptor.getBinding());
            samplerLayoutBinding.descriptorCount(1);
            samplerLayoutBinding.descriptorType(imageDescriptor.getType());
            samplerLayoutBinding.pImmutableSamplers(null);
            samplerLayoutBinding.stageFlags(imageDescriptor.getStages());
         }

         VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack);
         layoutInfo.sType(32);
         layoutInfo.pBindings(bindings);
         LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
         if (VK10.vkCreateDescriptorSetLayout(DeviceManager.vkDevice, layoutInfo, null, pDescriptorSetLayout) != 0) {
            throw new RuntimeException("Failed to create descriptor set layout");
         }

         this.descriptorSetLayout = pDescriptorSetLayout.get(0);
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

   protected void createPipelineLayout() {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
         pipelineLayoutInfo.sType(30);
         pipelineLayoutInfo.pSetLayouts(stack.longs(this.descriptorSetLayout));
         if (this.pushConstants != null) {
            org.lwjgl.vulkan.VkPushConstantRange.Buffer pushConstantRange = VkPushConstantRange.calloc(1, stack);
            pushConstantRange.size(this.pushConstants.getSize());
            pushConstantRange.offset(0);
            pushConstantRange.stageFlags(this.pushConstants.stages);
            pipelineLayoutInfo.pPushConstantRanges(pushConstantRange);
         }

         LongBuffer pPipelineLayout = stack.longs(0L);
         if (VK10.vkCreatePipelineLayout(DEVICE, pipelineLayoutInfo, null, pPipelineLayout) != 0) {
            throw new RuntimeException("Failed to create pipeline layout");
         }

         this.pipelineLayout = pPipelineLayout.get(0);
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

   protected void createDescriptorSets(int frames) {
      this.descriptorSets = new DescriptorSets[frames];

      for (int i = 0; i < frames; i++) {
         this.descriptorSets[i] = new DescriptorSets(this);
      }
   }

   public void scheduleCleanUp() {
      MemoryManager.getInstance().addFrameOp(this::cleanUp);
   }

   public abstract void cleanUp();

   protected void destroyDescriptorSets() {
      for (DescriptorSets descriptorSets : this.descriptorSets) {
         descriptorSets.cleanUp();
      }

      this.descriptorSets = null;
   }

   public void resetDescriptorPool(int i) {
      if (this.descriptorSets != null) {
         this.descriptorSets[i].resetIdx();
      }
   }

   public PushConstants getPushConstants() {
      return this.pushConstants;
   }

   public long getLayout() {
      return this.pipelineLayout;
   }

   public List<UBO> getBuffers() {
      return this.buffers;
   }

   public UBO getUBO(int binding) {
      this.buildMaps();
      return this.uboBindingMap.get(binding);
   }

   public UBO getUBO(String name) {
      this.buildMaps();
      return this.uboNameMap.get(name);
   }

   public UBO getUBO(Predicate<UBO> fn) {
      for (UBO ubo1 : this.buffers) {
         if (fn.test(ubo1)) {
            return ubo1;
         }
      }

      return null;
   }

   private void buildMaps() {
      if (!this.mapsBuilt && this.buffers != null) {
         this.uboNameMap.clear();
         this.uboBindingMap.clear();

         for (UBO ubo : this.buffers) {
            this.uboNameMap.put(ubo.name, ubo);
            this.uboBindingMap.put(ubo.binding, ubo);
         }

         this.mapsBuilt = true;
      }
   }

   public ImageDescriptor getImageDescriptor(String name) {
      return this.getImageDescriptor(imageDescriptor -> imageDescriptor.name.equals(name));
   }

   public ImageDescriptor getImageDescriptor(Predicate<ImageDescriptor> fn) {
      ImageDescriptor descriptor = null;

      for (ImageDescriptor descriptor1 : this.imageDescriptors) {
         if (fn.test(descriptor1)) {
            descriptor = descriptor1;
         }
      }

      return descriptor;
   }

   public List<ImageDescriptor> getImageDescriptors() {
      return this.imageDescriptors;
   }

   public void bindDescriptorSets(VkCommandBuffer commandBuffer, int frame) {
      UniformBuffer uniformBuffer = Renderer.getDrawer().getUniformBuffer();
      this.descriptorSets[frame].bindSets(commandBuffer, uniformBuffer, 0);
   }

   public void bindDescriptorSets(VkCommandBuffer commandBuffer, UniformBuffer uniformBuffer, int frame) {
      this.descriptorSets[frame].bindSets(commandBuffer, uniformBuffer, 0);
   }

   protected static long createShaderModule(ByteBuffer spirvCode) {
      MemoryStack stack = MemoryStack.stackPush();

      long var4;
      try {
         VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
         createInfo.sType(16);
         createInfo.pCode(spirvCode);
         LongBuffer pShaderModule = stack.mallocLong(1);
         if (VK10.vkCreateShaderModule(DEVICE, createInfo, null, pShaderModule) != 0) {
            throw new RuntimeException("Failed to create shader module");
         }

         var4 = pShaderModule.get(0);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }

      return var4;
   }

   public static Pipeline.Builder builder(VertexFormat vertexFormat, String path) {
      return new Pipeline.Builder(vertexFormat, path);
   }

   public static class Builder {
      public final VertexFormat vertexFormat;
      public final String name;
      List<UBO> UBOs = new ArrayList<>();
      List<ImageDescriptor> imageDescriptors = new ArrayList<>();
      PushConstants pushConstants;
      int nextBinding;
      Map<SPIRVUtils.ShaderKind, String> shadersSrc = new EnumMap<>(SPIRVUtils.ShaderKind.class);
      RenderPass renderPass;
      Function<Uniform.Info, Supplier<MappedBuffer>> uniformSupplierGetter;

      public Builder(VertexFormat vertexFormat, String name) {
         this.vertexFormat = vertexFormat;
         this.name = name;
      }

      public Builder(VertexFormat vertexFormat) {
         this(vertexFormat, null);
      }

      public Builder(String name) {
         this(null, name);
      }

      public Builder() {
         this(null, null);
      }

      public void setUniforms(List<UBO> UBOs, List<ImageDescriptor> imageDescriptors) {
         this.UBOs = UBOs;
         this.imageDescriptors = imageDescriptors;
      }

      public void setShaderSrc(SPIRVUtils.ShaderKind kind, String src) {
         this.shadersSrc.put(kind, src);
      }

      public void addUBO(UBO ubo) {
         this.UBOs.add(ubo);
      }

      public void addImageDescriptor(ImageDescriptor imageDescriptor) {
         this.imageDescriptors.add(imageDescriptor);
      }

      public Pipeline.Builder applyConfig(PipelineConfig config) {
         for (PipelineConfig.UB ub : config.ubs) {
            this.parseUboNode(ub);
         }

         for (PipelineConfig.ImageDescriptorInfo imageDescriptor : config.imageDescriptors) {
            this.parseImageDescriptor(imageDescriptor);
         }

         if (config.pushConstantsInfo != null) {
            this.parsePushConstantNode(config.pushConstantsInfo);
         }

         return this;
      }

      public void setUniformSupplierGetter(Function<Uniform.Info, Supplier<MappedBuffer>> uniformSupplierGetter) {
         this.uniformSupplierGetter = uniformSupplierGetter;
      }

      private void parseUboNode(PipelineConfig.UB ub) {
         int binding = ub.binding;
         int stages = ub.stage;
         AlignedStruct.Builder builder = new AlignedStruct.Builder();
         UBO ubo;
         if (!ub.uniforms.isEmpty()) {
            for (PipelineConfig.Uniform field : ub.uniforms) {
               String name = field.name();
               String type = field.type();
               Uniform.Info uniformInfo = Uniform.createUniformInfo(type, name);
               uniformInfo.setupSupplier();
               if (!uniformInfo.hasSupplier()) {
                  if (this.uniformSupplierGetter == null) {
                     throw new IllegalStateException("No uniform supplier found for uniform: (%s:%s)".formatted(type, name));
                  }

                  Supplier<MappedBuffer> uniformSupplier = this.uniformSupplierGetter.apply(uniformInfo);
                  if (uniformSupplier == null) {
                     throw new IllegalStateException("No uniform supplier found for uniform: (%s:%s)".formatted(type, name));
                  }

                  uniformInfo.setBufferSupplier(uniformSupplier);
               }

               builder.addUniform(uniformInfo);
            }

            ubo = builder.buildUBO(binding, stages);
         } else {
            int size = ub.size;
            if (size <= 0) {
               throw new IllegalStateException("Manual UBO has size <= 0");
            }

            ubo = new UBO("UBO %d".formatted(binding), binding, stages, size, null);
            ubo.setUseGlobalBuffer(false);
         }

         this.UBOs.add(ubo);
      }

      private void parseImageDescriptor(PipelineConfig.ImageDescriptorInfo info) {
         int descriptorType = switch (info.type()) {
            case "sampler2D" -> 1;
            case "image2D" -> 3;
            default -> throw new IllegalStateException("Unexpected value: " + info.type());
         };
         this.imageDescriptors.add(new ImageDescriptor(info.binding(), info.type(), info.name(), info.imageIdx(), descriptorType));
      }

      private void parsePushConstantNode(PipelineConfig.UB ub) {
         AlignedStruct.Builder builder = new AlignedStruct.Builder();
         int stages = ub.stage;

         for (PipelineConfig.Uniform field : ub.uniforms) {
            String name = field.name();
            String type = field.type();
            Uniform.Info uniformInfo = Uniform.createUniformInfo(type, name);
            builder.addUniform(uniformInfo);
         }

         this.pushConstants = builder.buildPushConstant(stages);
      }

      public List<UBO> getUBOs() {
         return this.UBOs;
      }

      public List<ImageDescriptor> getImageDescriptors() {
         return this.imageDescriptors;
      }

      public PushConstants getPushConstants() {
         return this.pushConstants;
      }

      public Map<SPIRVUtils.ShaderKind, String> getShadersSrc() {
         return this.shadersSrc;
      }

      public GraphicsPipeline createGraphicsPipeline() {
         return new GraphicsPipeline(this);
      }
   }
}
