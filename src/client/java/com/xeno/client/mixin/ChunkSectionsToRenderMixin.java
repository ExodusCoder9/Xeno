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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(ChunkSectionsToRender.class)
@SuppressWarnings("resource")
public abstract class ChunkSectionsToRenderMixin {
    @Shadow @Final private com.mojang.blaze3d.textures.GpuTextureView textureView;
    @Shadow @Final private java.util.EnumMap<ChunkSectionLayer, it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<java.util.List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroupsPerLayer;
    @Shadow @Final private int maxIndicesRequired;
    @Shadow @Final private GpuBufferSlice[] chunkSectionInfos;

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

        // Lazily initialize our thread-safe dynamic UBO on the GPU (OpenGL path)
        if (XenoClient.xenoTempBufferHandle == 0) {
            XenoClient.xenoTempBufferHandle = org.lwjgl.opengl.GL15C.glGenBuffers();
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER, XenoClient.xenoTempBufferHandle);
            org.lwjgl.opengl.GL15C.glBufferData(org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER, 65536L, org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW);
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

                        // Perform True Multi-Draw Call
                        try (MemoryStack stack = MemoryStack.stackPush()) {
                            org.lwjgl.PointerBuffer firstIndexOffsets = stack.mallocPointer(drawCount);
                            java.nio.IntBuffer indexCounts = stack.mallocInt(drawCount);
                            java.nio.IntBuffer vertexOffsets = stack.mallocInt(drawCount);

                            RenderPass.Draw<GpuBufferSlice[]> firstDraw = activeDraws.getFirst();

                            // Bind Vertex & Index Buffers
                            renderPass.setVertexBuffer(firstDraw.slot(), firstDraw.vertexBuffer().slice());

                            GpuBuffer indexBuffer = firstDraw.indexBuffer() != null ? firstDraw.indexBuffer() : defaultIndexBuffer;
                            IndexType indexType = firstDraw.indexBuffer() != null ? Objects.requireNonNull(firstDraw.indexType()) : Objects.requireNonNull(defaultIndexType);
                            if (indexBuffer != null) {
                                renderPass.setIndexBuffer(indexBuffer, indexType);
                            }

                            // Copy UBO slices on the GPU to make them contiguous
                            for (int i = 0; i < drawCount; i++) {
                                RenderPass.Draw<GpuBufferSlice[]> draw = activeDraws.get(i);
                                firstIndexOffsets.put(i, (long) draw.firstIndex() * indexType.bytes);
                                indexCounts.put(i, draw.indexCount());
                                vertexOffsets.put(i, draw.baseVertex());

                                final int index = i;
                                var consumer = draw.uniformUploaderConsumer();
                                if (consumer != null) {
                                    consumer.accept(this.chunkSectionInfos, (ignored, slice) -> {
                                        int handle = ((com.mojang.blaze3d.opengl.GlBuffer) slice.buffer()).handle();
                                        long srcOffset = slice.offset();
                                        long dstOffset = (long) index * 112L;

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

                            // Bind our populated contiguous UBO buffer
                            org.lwjgl.opengl.GL30C.glBindBufferRange(
                                org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER,
                                blockBinding,
                                XenoClient.xenoTempBufferHandle,
                                0L,
                                (long) drawCount * 112L
                            );

                            // Execute Multi-Draw Call
                            renderPass.multiDrawIndexed(firstIndexOffsets, indexCounts, vertexOffsets, drawCount);
                        }
                    }
                }
            }
        }
    }
}
