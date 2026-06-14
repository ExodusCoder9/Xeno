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
    private boolean capturing;
    private final List<RecordedDraw> draws = new ArrayList<>();

    public void setCapturing(boolean capturing) {
        this.capturing = capturing;
    }

    public boolean isCapturing() {
        return this.capturing;
    }

    public void record(RecordedDraw draw) {
        this.draws.add(draw);
    }

    public void flush() {
        if (!this.capturing || this.draws.isEmpty()) {
            this.capturing = false;
            return;
        }

        this.capturing = false;
        Map<RenderPipeline, List<RecordedDraw>> groups = new LinkedHashMap<>();
        for (RecordedDraw draw : this.draws) {
            groups.computeIfAbsent(draw.pipeline, k -> new ArrayList<>()).add(draw);
        }

        VkCommandEncoder commandEncoder = (VkCommandEncoder)RenderSystem.getDevice().createCommandEncoder().backend;
        Renderer renderer = Renderer.getInstance();

        for (Map.Entry<RenderPipeline, List<RecordedDraw>> entry : groups.entrySet()) {
            List<RecordedDraw> group = entry.getValue();
            RecordedDraw first = group.get(0);

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Entity batch: " + first.name,
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

                for (RecordedDraw draw : group) {
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
        }

        this.draws.clear();
    }

    public static class RecordedDraw {
        public final RenderPipeline pipeline;
        public final GpuTextureView colorTextureView;
        public final GpuTextureView depthTextureView;
        public final String name;
        public final Map<String, RenderSetup.TextureAndSampler> textures;
        public final GpuBufferSlice uniformSlice;
        public final VertexBuffer vertexBuffer;
        public final long vertexOffset;
        public final Buffer indexBuffer;
        public final long indexOffset;
        public final int indexCount;
        public final int indexType;
        public final ScissorState scissorState;

        public RecordedDraw(
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
