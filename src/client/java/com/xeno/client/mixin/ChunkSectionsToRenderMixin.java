package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.IndexType;
import org.lwjgl.system.MemoryStack;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.textures.FilterMode;
import org.spongepowered.asm.mixin.*;

@Mixin(ChunkSectionsToRender.class)
@SuppressWarnings("resource")
public abstract class ChunkSectionsToRenderMixin {
    @Shadow @Final private com.mojang.blaze3d.textures.GpuTextureView textureView;
    @Shadow @Final private java.util.EnumMap<ChunkSectionLayer, it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<java.util.List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroupsPerLayer;
    @Shadow @Final private int maxIndicesRequired;
    @Shadow @Final private GpuBufferSlice[] chunkSectionInfos;

    @Unique
    private static int xenoTempBufferSize = 0;

    /**
     * @author ExodusCoder9
     * @reason Implementation of True Multi-Draw rendering (glMultiDrawElementsBaseVertex) for chunk sections.
     */
    @Overwrite
    public void renderGroup(final ChunkSectionLayerGroup group, final GpuSampler sampler) {
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer defaultIndexBuffer = this.maxIndicesRequired == 0 ? null : autoIndices.getBuffer(this.maxIndicesRequired);
        IndexType defaultIndexType = this.maxIndicesRequired == 0 ? null : autoIndices.type();
        ChunkSectionLayer[] layers = group.layers();
        Minecraft minecraft = Minecraft.getInstance();
        boolean wireframe = SharedConstants.DEBUG_HOTKEYS && minecraft.wireframe;
        com.mojang.blaze3d.pipeline.RenderTarget renderTarget = group.outputTarget();

        boolean isGl = RenderSystem.getDevice().getClass().getName().contains("GlDevice");

        if (!isGl) {
            // Vulkan rendering path fallback (uses standard drawMultipleIndexed)
            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                       () -> "Section layers for " + group.label(),
                       Objects.requireNonNull(renderTarget.getColorTextureView(), "Color texture view is null"),
                       Optional.empty(),
                       renderTarget.getDepthTextureView(),
                       OptionalDouble.empty()
                    )) {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("Sampler0", this.textureView, sampler);
                renderPass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

                for (ChunkSectionLayer layer : layers) {
                    renderPass.setPipeline(wireframe ? RenderPipelines.WIREFRAME : layer.pipeline());
                    it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup = this.drawGroupsPerLayer.get(layer);

                    for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                        if (!draws.isEmpty()) {
                            List<RenderPass.Draw<GpuBufferSlice[]>> activeDraws = draws;
                            if (layer == ChunkSectionLayer.TRANSLUCENT) {
                                activeDraws = draws.reversed();
                            }
                            renderPass.drawMultipleIndexed(activeDraws, defaultIndexBuffer, defaultIndexType, List.of("ChunkSection"), this.chunkSectionInfos);
                        }
                    }
                }
            }
            return;
        }

        // Lazily initialize/resize our thread-safe dynamic UBO on the GPU (OpenGL path)
        int maxDrawCount = 0;
        for (ChunkSectionLayer layer : layers) {
            it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup = this.drawGroupsPerLayer.get(layer);
            for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                maxDrawCount = Math.max(maxDrawCount, draws.size());
            }
        }
        int requiredSize = maxDrawCount * 112;
        if (XenoClient.xenoTempBufferHandle == 0) {
            XenoClient.xenoTempBufferHandle = org.lwjgl.opengl.GL15C.glGenBuffers();
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER, XenoClient.xenoTempBufferHandle);
            int initialSize = Math.max(1048576, requiredSize);
            org.lwjgl.opengl.GL15C.glBufferData(org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER, initialSize, org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW);
            xenoTempBufferSize = initialSize;
        } else if (requiredSize > xenoTempBufferSize) {
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER, XenoClient.xenoTempBufferHandle);
            org.lwjgl.opengl.GL15C.glBufferData(org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER, requiredSize, org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW);
            xenoTempBufferSize = requiredSize;
        }

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                   () -> "Section layers for " + group.label(),
                   Objects.requireNonNull(renderTarget.getColorTextureView(), "Color texture view is null"),
                   Optional.empty(),
                   renderTarget.getDepthTextureView(),
                   OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("Sampler0", this.textureView, sampler);
            renderPass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

            for (ChunkSectionLayer layer : layers) {
                com.mojang.blaze3d.pipeline.RenderPipeline pipeline = wireframe ? RenderPipelines.WIREFRAME : layer.pipeline();
                renderPass.setPipeline(pipeline);

                it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup = this.drawGroupsPerLayer.get(layer);

                for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                    if (!draws.isEmpty()) {
                        List<RenderPass.Draw<GpuBufferSlice[]>> activeDraws = draws;
                        if (layer == ChunkSectionLayer.TRANSLUCENT) {
                            activeDraws = draws.reversed();
                        }

                        int drawCount = activeDraws.size();

                        // Bind Vertex & Index Buffers
                        RenderPass.Draw<GpuBufferSlice[]> firstDraw = activeDraws.getFirst();
                        renderPass.setVertexBuffer(firstDraw.slot(), firstDraw.vertexBuffer().slice());

                        GpuBuffer indexBuffer = firstDraw.indexBuffer() != null ? firstDraw.indexBuffer() : defaultIndexBuffer;
                        IndexType indexType = firstDraw.indexBuffer() != null ? Objects.requireNonNull(firstDraw.indexType()) : Objects.requireNonNull(defaultIndexType);
                        if (indexBuffer != null) {
                            renderPass.setIndexBuffer(indexBuffer, indexType);
                        }

                        // Resolve the UBO binding point dynamically using Invoker
                        int blockBinding = 0;
                        com.mojang.blaze3d.opengl.GlRenderPipeline glPipeline = ((com.xeno.client.mixin.GlDeviceInvoker) RenderSystem.getDevice()).invokeGetOrCompilePipeline(pipeline);
                        if (glPipeline != null) {
                            com.mojang.blaze3d.opengl.GlProgram program = glPipeline.program();
                            com.mojang.blaze3d.opengl.Uniform uniform = program.getUniform("ChunkSection");
                            if (uniform instanceof com.mojang.blaze3d.opengl.Uniform.Ubo(int binding)) {
                                blockBinding = binding;
                            }
                        }

                        // Batch draws in sizes of 512 to comply with UBO memory layouts and prevent out-of-bounds array access in the shader.
                        // We also bind the range with a minimum of 57344 bytes to avoid OpenGL driver failures/undefined behavior on small sizes.
                        int BATCH_SIZE = 512;
                        try (MemoryStack _ = MemoryStack.stackPush()) {
                            for (int offset = 0; offset < drawCount; offset += BATCH_SIZE) {
                                final int batchStartOffset = offset;
                                int batchCount = Math.min(BATCH_SIZE, drawCount - offset);

                                try (MemoryStack batchStack = MemoryStack.stackPush()) {
                                    org.lwjgl.PointerBuffer batchIndexOffsets = batchStack.mallocPointer(batchCount);
                                    java.nio.IntBuffer batchIndexCounts = batchStack.mallocInt(batchCount);
                                    java.nio.IntBuffer batchVertexOffsets = batchStack.mallocInt(batchCount);

                                    for (int i = 0; i < batchCount; i++) {
                                        RenderPass.Draw<GpuBufferSlice[]> draw = activeDraws.get(batchStartOffset + i);
                                        batchIndexOffsets.put(i, (long) draw.firstIndex() * indexType.bytes);
                                        batchIndexCounts.put(i, draw.indexCount());
                                        batchVertexOffsets.put(i, draw.baseVertex());

                                        final int index = i;
                                        var consumer = draw.uniformUploaderConsumer();
                                        if (consumer != null) {
                                            consumer.accept(this.chunkSectionInfos, (ignored, slice) -> {
                                                int handle = ((com.mojang.blaze3d.opengl.GlBuffer) slice.buffer()).handle();
                                                long srcOffset = slice.offset();
                                                long dstOffset = (long) batchStartOffset * 112L + (long) index * 112L;

                                                org.lwjgl.opengl.GL31C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER, handle);
                                                org.lwjgl.opengl.GL31C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER, XenoClient.xenoTempBufferHandle);
                                                org.lwjgl.opengl.GL31C.glCopyBufferSubData(
                                                    org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER,
                                                    org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER,
                                                    srcOffset,
                                                    dstOffset,
                                                    112L
                                                );
                                            });
                                        }
                                    }

                                    // Bind the batch's populated range (57344 bytes = 512 * 112 bytes)
                                    org.lwjgl.opengl.GL30C.glBindBufferRange(
                                        org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER,
                                        blockBinding,
                                        XenoClient.xenoTempBufferHandle,
                                        (long) batchStartOffset * 112L,
                                        57344L
                                    );

                                    // Execute Multi-Draw Call for this batch
                                    renderPass.multiDrawIndexed(batchIndexOffsets, batchIndexCounts, batchVertexOffsets, batchCount);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
