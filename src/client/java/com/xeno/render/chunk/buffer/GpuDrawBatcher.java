package com.xeno.render.chunk.buffer;

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


import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import com.xeno.Initializer;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.shader.ShaderLoadUtil;
import com.xeno.render.vertex.TerrainRenderType;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.memory.MemoryTypes;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.shader.SPIRVUtils;
import com.xeno.vulkan.util.VUtil;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.joml.Vector3d;

/**
 * GPU-driven draw batch builder. Replaces the CPU-side section iteration
 * and face-culling loop with a Vulkan compute shader dispatch.
 */
public class GpuDrawBatcher {
    private static final Logger LOGGER = Initializer.LOGGER;
    private static final int MAX_SECTIONS = 512;
    private static final int FACINGS_PER_RENDER_TYPE = QuadFacing.COUNT;
    private static final int DRAW_PARAMS_PER_SECTION = TerrainRenderType.VALUES.length * FACINGS_PER_RENDER_TYPE;
    private static final int MAX_DRAW_PARAMS = MAX_SECTIONS * DRAW_PARAMS_PER_SECTION * 4;
    private static final int MAX_OUTPUT_COMMANDS = MAX_SECTIONS * FACINGS_PER_RENDER_TYPE;
    private static final int SECTION_STRIDE = 16;
    private static final int DRAW_PARAM_STRIDE = 16;
    private static final int OUTPUT_STRIDE = 20;

    private static GpuDrawBatcher INSTANCE;
    private boolean initialized;

    private long pipelineLayout;
    private long computePipeline;
    private long descriptorSetLayout;
    private long descriptorPool;
    private long[] descriptorSets;

    private Buffer sectionBuffer;
    private Buffer drawParamBuffer;
    private Buffer outputBuffer;
    private FloatBuffer sectionMapped;
    private IntBuffer drawParamMapped;

    private long uniformBufferId;
    private long uniformBufferAlloc;
    private long uniformMappedPtr;

    public static GpuDrawBatcher getInstance() {
        if (INSTANCE == null) INSTANCE = new GpuDrawBatcher();
        return INSTANCE;
    }

    public void init() {
        if (initialized) return;
        try {
            createComputePipeline();
            createBuffers();
            createDescriptorSets();
            initialized = true;
            LOGGER.info("GPU-driven draw batcher initialized");
        } catch (Exception e) {
            LOGGER.error("Failed to init GPU draw batcher, falling back to CPU path", e);
            initialized = false;
        }
    }

    private void createComputePipeline() {
        MemoryStack stack = MemoryStack.stackPush();
        try {
            String path = ShaderLoadUtil.SHADERS_PATH + "compute/";
            String shaderSrc = ShaderLoadUtil.getShaderSource(path, "gpu_cull.comp");
            if (shaderSrc == null) throw new RuntimeException("GPU cull compute shader not found");
            SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShader("gpu_cull", shaderSrc, SPIRVUtils.ShaderKind.COMPUTE_SHADER);
            ByteBuffer spirvCode = spirv.bytecode();

            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack);
            moduleInfo.sType(16);
            moduleInfo.pCode(spirvCode);
            LongBuffer pModule = stack.mallocLong(1);
            VK10.vkCreateShaderModule(Vulkan.getVkDevice(), moduleInfo, null, pModule);
            long shaderModule = pModule.get(0);
            spirv.free();

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
            bindings.get(0).binding(0).descriptorType(6).descriptorCount(1).stageFlags(32);
            bindings.get(1).binding(1).descriptorType(7).descriptorCount(1).stageFlags(32);
            bindings.get(2).binding(2).descriptorType(7).descriptorCount(1).stageFlags(32);
            bindings.get(3).binding(3).descriptorType(7).descriptorCount(1).stageFlags(32);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack);
            layoutInfo.sType(35);
            layoutInfo.pBindings(bindings);
            LongBuffer pLayout = stack.mallocLong(1);
            VK10.vkCreateDescriptorSetLayout(Vulkan.getVkDevice(), layoutInfo, null, pLayout);
            descriptorSetLayout = pLayout.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutInfo.sType(39);
            pipelineLayoutInfo.pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer pPipelineLayout = stack.mallocLong(1);
            VK10.vkCreatePipelineLayout(Vulkan.getVkDevice(), pipelineLayoutInfo, null, pPipelineLayout);
            pipelineLayout = pPipelineLayout.get(0);

            VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack);
            stageInfo.sType(18);
            stageInfo.stage(32);
            stageInfo.module(shaderModule);
            stageInfo.pName(stack.UTF8("main"));

            VkComputePipelineCreateInfo.Buffer computeInfoBuf = VkComputePipelineCreateInfo.calloc(1, stack);
            computeInfoBuf.get(0).sType(29).stage(stageInfo).layout(pipelineLayout);

            LongBuffer pPipeline = stack.mallocLong(1);
            VK10.vkCreateComputePipelines(Vulkan.getVkDevice(), 0L, computeInfoBuf, null, pPipeline);
            computePipeline = pPipeline.get(0);

            VK10.vkDestroyShaderModule(Vulkan.getVkDevice(), shaderModule, null);
        } finally {
            stack.close();
        }
    }

    private void createBuffers() {
        long sectionSize = (long)MAX_SECTIONS * SECTION_STRIDE;
        long drawParamSize = (long)MAX_DRAW_PARAMS * DRAW_PARAM_STRIDE;
        long outputSize = (long)MAX_OUTPUT_COMMANDS * OUTPUT_STRIDE;

        sectionBuffer = new Buffer("GpuSectionSSBO", 128 | 2, MemoryTypes.HOST_MEM);
        sectionBuffer.createBuffer(sectionSize);
        sectionMapped = MemoryUtil.memFloatBuffer(sectionBuffer.getDataPtr(), (int)(sectionSize / 4));

        drawParamBuffer = new Buffer("GpuDrawParamSSBO", 128 | 2, MemoryTypes.HOST_MEM);
        drawParamBuffer.createBuffer(drawParamSize);
        drawParamMapped = MemoryUtil.memIntBuffer(drawParamBuffer.getDataPtr(), (int)(drawParamSize / 4));

        outputBuffer = new Buffer("GpuOutputSSBO", 128 | 256, MemoryTypes.GPU_MEM);
        outputBuffer.createBuffer(outputSize);

        LongBuffer pBuf = MemoryUtil.memAllocLong(1);
        PointerBuffer pAlloc = MemoryUtil.memAllocPointer(1);
        MemoryManager.getInstance().createBuffer(32, 4, 11, pBuf, pAlloc);
        uniformBufferId = pBuf.get(0);
        uniformBufferAlloc = pAlloc.get(0);
        uniformMappedPtr = MemoryManager.getInstance().Map(uniformBufferAlloc).get(0);
        MemoryUtil.memFree(pBuf);
        MemoryUtil.memFree(pAlloc);
    }

    private void createDescriptorSets() {
        MemoryStack stack = MemoryStack.stackPush();
        try {
            VkDescriptorPoolSize.Buffer poolSizeBuf = VkDescriptorPoolSize.calloc(2, stack);
            poolSizeBuf.get(0).type(6).descriptorCount(1);
            poolSizeBuf.get(1).type(7).descriptorCount(3);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack);
            poolInfo.sType(33);
            poolInfo.maxSets(4);
            poolInfo.pPoolSizes(poolSizeBuf);
            LongBuffer pPool = stack.mallocLong(1);
            VK10.vkCreateDescriptorPool(Vulkan.getVkDevice(), poolInfo, null, pPool);
            descriptorPool = pPool.get(0);

            descriptorSets = new long[2];
            LongBuffer layouts = stack.mallocLong(2);
            layouts.put(0, descriptorSetLayout);
            layouts.put(1, descriptorSetLayout);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack);
            allocInfo.sType(34);
            allocInfo.descriptorPool(descriptorPool);
            allocInfo.pSetLayouts(layouts);
            LongBuffer pSets = stack.mallocLong(2);
            VK10.vkAllocateDescriptorSets(Vulkan.getVkDevice(), allocInfo, pSets);
            descriptorSets[0] = pSets.get(0);
            descriptorSets[1] = pSets.get(1);

            writeDescriptorSets(stack, 0);
            writeDescriptorSets(stack, 1);
        } finally {
            stack.close();
        }
    }

    private void writeDescriptorSets(MemoryStack stack, int frame) {
        VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(4, stack);
        bufferInfos.get(0).buffer(uniformBufferId).offset(0).range(32);
        bufferInfos.get(1).buffer(sectionBuffer.getId()).offset(0).range(sectionBuffer.getBufferSize());
        bufferInfos.get(2).buffer(drawParamBuffer.getId()).offset(0).range(drawParamBuffer.getBufferSize());
        bufferInfos.get(3).buffer(outputBuffer.getId()).offset(0).range(outputBuffer.getBufferSize());

        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);
        writes.get(0).sType(49).dstSet(descriptorSets[frame]).dstBinding(0).descriptorCount(1).descriptorType(6).pBufferInfo(bufferInfos.position(0));
        writes.get(1).sType(49).dstSet(descriptorSets[frame]).dstBinding(1).descriptorCount(1).descriptorType(7).pBufferInfo(bufferInfos.position(1));
        writes.get(2).sType(49).dstSet(descriptorSets[frame]).dstBinding(2).descriptorCount(1).descriptorType(7).pBufferInfo(bufferInfos.position(2));
        writes.get(3).sType(49).dstSet(descriptorSets[frame]).dstBinding(3).descriptorCount(1).descriptorType(7).pBufferInfo(bufferInfos.position(3));

        VK10.vkUpdateDescriptorSets(Vulkan.getVkDevice(), writes, null);
    }

    public int buildBatchesGpu(
        VkCommandBuffer cmdBuffer,
        RenderSection[] sections, int sectionCount,
        Vector3d cameraPos, TerrainRenderType renderType,
        long drawParamsHostPtr, int currentFrame
    ) {
        if (!initialized || sectionCount == 0) return 0;

        for (int i = 0; i < sectionCount; i++) {
            RenderSection s = sections[i];
            int off = i * 4;
            sectionMapped.put(off, (float)s.xOffset());
            sectionMapped.put(off + 1, (float)s.yOffset());
            sectionMapped.put(off + 2, (float)s.zOffset());
            sectionMapped.put(off + 3, s.inAreaIndex);
        }

        int dpCount = MAX_DRAW_PARAMS;
        long dpSize = (long)dpCount * 4;
        MemoryUtil.memCopy(drawParamsHostPtr, MemoryUtil.memAddress(drawParamMapped), dpSize);

        MemoryUtil.memPutFloat(uniformMappedPtr, (float)cameraPos.x);
        MemoryUtil.memPutFloat(uniformMappedPtr + 4, (float)cameraPos.y);
        MemoryUtil.memPutFloat(uniformMappedPtr + 8, (float)cameraPos.z);
        MemoryUtil.memPutInt(uniformMappedPtr + 12, sectionCount);
        MemoryUtil.memPutInt(uniformMappedPtr + 16, renderType.ordinal());
        MemoryUtil.memPutInt(uniformMappedPtr + 20, (Initializer.CONFIG.backFaceCulling && renderType != TerrainRenderType.TRANSLUCENT) ? 1 : 0);

        VK10.vkCmdBindPipeline(cmdBuffer, 1, computePipeline);
        MemoryStack bindStack = MemoryStack.stackPush();
        try {
            LongBuffer dsBuf = bindStack.longs(descriptorSets[currentFrame % descriptorSets.length]);
            VK10.vkCmdBindDescriptorSets(cmdBuffer, 1, pipelineLayout, 0, dsBuf, null);
        } finally {
            bindStack.close();
        }

        int workgroups = (sectionCount + 63) / 64;
        VK10.vkCmdDispatch(cmdBuffer, workgroups, 1, 1);

        MemoryStack barrierStack = MemoryStack.stackPush();
        try {
            VkBufferMemoryBarrier.Buffer bufBarrier = VkBufferMemoryBarrier.calloc(1, barrierStack);
            bufBarrier.sType(44);
            bufBarrier.srcAccessMask(4096);
            bufBarrier.dstAccessMask(1048576);
            bufBarrier.buffer(outputBuffer.getId());
            bufBarrier.offset(0);
            bufBarrier.size(outputBuffer.getBufferSize());
            VK10.vkCmdPipelineBarrier(cmdBuffer, 32, 2, 0, null, bufBarrier, null);
        } finally {
            barrierStack.close();
        }

        VK10.vkCmdDrawIndexedIndirect(cmdBuffer, outputBuffer.getId(), 0, sectionCount * FACINGS_PER_RENDER_TYPE, OUTPUT_STRIDE);

        return sectionCount * FACINGS_PER_RENDER_TYPE;
    }

    public boolean isInitialized() { return initialized; }

    public void destroy() {
        if (!initialized) return;
        VK10.vkDestroyPipeline(Vulkan.getVkDevice(), computePipeline, null);
        VK10.vkDestroyPipelineLayout(Vulkan.getVkDevice(), pipelineLayout, null);
        VK10.vkDestroyDescriptorSetLayout(Vulkan.getVkDevice(), descriptorSetLayout, null);
        VK10.vkDestroyDescriptorPool(Vulkan.getVkDevice(), descriptorPool, null);
        MemoryManager.freeBuffer(uniformBufferId, uniformBufferAlloc);
        sectionBuffer.scheduleFree();
        drawParamBuffer.scheduleFree();
        outputBuffer.scheduleFree();
        initialized = false;
    }
}
