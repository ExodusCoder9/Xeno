package com.xeno.render.entity;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xeno.render.engine.VkCommandEncoder;
import com.xeno.render.engine.VkRenderPass;
import com.xeno.vulkan.Drawer;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.VRenderSystem;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.memory.buffer.VertexBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.lwjgl.vulkan.VK10;

public class EntityBatcher {
    private static final java.util.Comparator<RecordedDraw> DRAW_COMPARATOR = (d1, d2) -> {
        int h1 = System.identityHashCode(d1.pipeline);
        int h2 = System.identityHashCode(d2.pipeline);
        return Integer.compare(h1, h2);
    };

    private static class BatchNameSupplier implements java.util.function.Supplier<String> {
        String name;
        @Override
        public String get() {
            return "Entity batch: " + name;
        }
    }

    private boolean capturing;
    private final List<RecordedDraw> draws = new ArrayList<>();
    private final List<RecordedDraw> drawPool = new ArrayList<>();
    private int poolIndex = 0;
    private final BatchNameSupplier nameSupplier = new BatchNameSupplier();

    public void setCapturing(boolean capturing) {
        this.capturing = capturing;
    }

    public boolean isCapturing() {
        return this.capturing;
    }

    public void record(RecordedDraw draw) {
        this.draws.add(draw);
    }

    public RecordedDraw obtainDraw(
        RenderPipeline pipeline,
        GpuTextureView colorTextureView,
        GpuTextureView depthTextureView,
        String name,
        Map<String, RenderSetup.TextureAndSampler> textures,
        GpuBufferSlice uniformSlice,
        VertexBuffer vertexBuffer,
        long vertexOffset,
        Buffer indexBuffer,
        long indexOffset,
        int indexCount,
        int indexType,
        ScissorState scissorState
    ) {
        RecordedDraw draw;
        if (poolIndex < drawPool.size()) {
            draw = drawPool.get(poolIndex);
            draw.reset(
                pipeline, colorTextureView, depthTextureView, name, textures,
                uniformSlice, vertexBuffer, vertexOffset, indexBuffer, indexOffset,
                indexCount, indexType, scissorState
            );
        } else {
            draw = new RecordedDraw(
                pipeline, colorTextureView, depthTextureView, name, textures,
                uniformSlice, vertexBuffer, vertexOffset, indexBuffer, indexOffset,
                indexCount, indexType, scissorState
            );
            drawPool.add(draw);
        }
        poolIndex++;
        return draw;
    }

    public void flush() {
        if (!this.capturing || this.draws.isEmpty()) {
            this.capturing = false;
            this.poolIndex = 0;
            return;
        }

        this.capturing = false;
        this.draws.sort(DRAW_COMPARATOR);

        VkCommandEncoder commandEncoder = (VkCommandEncoder)RenderSystem.getDevice().createCommandEncoder().backend;
        Renderer renderer = Renderer.getInstance();

        int i = 0;
        int totalDraws = this.draws.size();
        while (i < totalDraws) {
            RecordedDraw first = this.draws.get(i);
            RenderPipeline currentPipeline = first.pipeline;

            int end = i + 1;
            while (end < totalDraws && this.draws.get(end).pipeline == currentPipeline) {
                end++;
            }

            this.nameSupplier.name = first.name;

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(this.nameSupplier,
                        first.colorTextureView, OptionalInt.empty(), first.depthTextureView, OptionalDouble.empty())) {

                renderPass.setPipeline(first.pipeline);
                ScissorState scissorState = first.scissorState;
                if (scissorState.enabled()) {
                    renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
                }

                RenderSystem.bindDefaultUniforms(renderPass);

                for (Map.Entry<String, RenderSetup.TextureAndSampler> texEntry : first.textures.entrySet()) {
                    renderPass.bindTexture(texEntry.getKey(), texEntry.getValue().textureView(), texEntry.getValue().sampler());
                }

                boolean firstInGroup = true;

                for (int j = i; j < end; j++) {
                    RecordedDraw draw = this.draws.get(j);
                    if (!firstInGroup) {
                        for (Map.Entry<String, RenderSetup.TextureAndSampler> texEntry : draw.textures.entrySet()) {
                            renderPass.bindTexture(texEntry.getKey(), texEntry.getValue().textureView(), texEntry.getValue().sampler());
                        }
                        if (draw.scissorState.enabled()) {
                            renderPass.enableScissor(draw.scissorState.x(), draw.scissorState.y(), draw.scissorState.width(), draw.scissorState.height());
                        } else {
                            renderPass.disableScissor();
                        }
                    }

                    renderPass.setUniform("DynamicTransforms", draw.uniformSlice);
                    commandEncoder.trySetup((VkRenderPass)renderPass.backend);

                    Renderer.getDrawer().drawPrepared(
                        draw.vertexBuffer, draw.vertexOffset,
                        draw.indexBuffer, draw.indexOffset,
                        draw.indexCount, draw.indexType
                    );

                    firstInGroup = false;
                }
            }

            i = end;
        }

        this.draws.clear();
        this.poolIndex = 0;
    }

    public static class RecordedDraw {
        public RenderPipeline pipeline;
        public GpuTextureView colorTextureView;
        public GpuTextureView depthTextureView;
        public String name;
        public Map<String, RenderSetup.TextureAndSampler> textures;
        public GpuBufferSlice uniformSlice;
        public VertexBuffer vertexBuffer;
        public long vertexOffset;
        public Buffer indexBuffer;
        public long indexOffset;
        public int indexCount;
        public int indexType;
        public ScissorState scissorState;

        private RecordedDraw(
            RenderPipeline pipeline,
            GpuTextureView colorTextureView,
            GpuTextureView depthTextureView,
            String name,
            Map<String, RenderSetup.TextureAndSampler> textures,
            GpuBufferSlice uniformSlice,
            VertexBuffer vertexBuffer,
            long vertexOffset,
            Buffer indexBuffer,
            long indexOffset,
            int indexCount,
            int indexType,
            ScissorState scissorState
        ) {
            this.reset(
                pipeline, colorTextureView, depthTextureView, name, textures,
                uniformSlice, vertexBuffer, vertexOffset, indexBuffer, indexOffset,
                indexCount, indexType, scissorState
            );
        }

        public void reset(
            RenderPipeline pipeline,
            GpuTextureView colorTextureView,
            GpuTextureView depthTextureView,
            String name,
            Map<String, RenderSetup.TextureAndSampler> textures,
            GpuBufferSlice uniformSlice,
            VertexBuffer vertexBuffer,
            long vertexOffset,
            Buffer indexBuffer,
            long indexOffset,
            int indexCount,
            int indexType,
            ScissorState scissorState
        ) {
            this.pipeline = pipeline;
            this.colorTextureView = colorTextureView;
            this.depthTextureView = depthTextureView;
            this.name = name;
            this.textures = textures;
            this.uniformSlice = uniformSlice;
            this.vertexBuffer = vertexBuffer;
            this.vertexOffset = vertexOffset;
            this.indexBuffer = indexBuffer;
            this.indexOffset = indexOffset;
            this.indexCount = indexCount;
            this.indexType = indexType;
            this.scissorState = scissorState;
        }
    }
}
