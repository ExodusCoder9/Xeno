package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.logging.LogUtils;
import com.xeno.client.XenoClient;
import com.xeno.client.util.XenoMeshExtension;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Map;

public final class XenoWorldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable XenoWorldRenderer instance;

    private int compileSectionsSkipped;
    private int compileSectionsProcessed;
    private int framesSinceInit;
    private boolean active;

    public XenoWorldRenderer() {
        this.active = true;
        LOGGER.info("[Xeno] World renderer initialized");
    }

    public static void setInstance(@Nullable XenoWorldRenderer renderer) {
        instance = renderer;
    }

    public static @Nullable XenoWorldRenderer getInstance() {
        return instance;
    }

    public void reload() {
        LOGGER.info("[Xeno] World renderer reloading");
        this.compileSectionsSkipped = 0;
        this.compileSectionsProcessed = 0;
        this.framesSinceInit = 0;
        this.active = true;
    }

    public void onCompileSectionsFrame(int skipped, int processed) {
        this.compileSectionsSkipped = skipped;
        this.compileSectionsProcessed = processed;
        this.framesSinceInit++;
    }

    public boolean isActive() {
        return this.active;
    }

    public int getCompileSectionsSkipped() {
        return this.compileSectionsSkipped;
    }

    public int getCompileSectionsProcessed() {
        return this.compileSectionsProcessed;
    }

    public int getFramesSinceInit() {
        return this.framesSinceInit;
    }

    public void destroy() {
        this.active = false;
        this.compileSectionsSkipped = 0;
        this.compileSectionsProcessed = 0;
        this.framesSinceInit = 0;
        LOGGER.info("[Xeno] World renderer destroyed");
    }

    /**
     * Uploads the compiled mesh results to graphics-backend independent GpuBuffers.
     */
    public static void uploadToGpu(SectionRenderDispatcher.RenderSection section, net.minecraft.client.renderer.chunk.SectionCompiler.Results results) {
        Vec3 cameraPos = XenoClient.getCameraPos();
        long sectionNode = section.getSectionNode();
        TranslucencyPointOfView pointOfView = TranslucencyPointOfView.of(cameraPos != null ? cameraPos : Vec3.ZERO, sectionNode);
        CompiledSectionMesh compiled = new CompiledSectionMesh(pointOfView, results);

        GpuDevice device = RenderSystem.getDevice();

        for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
            ChunkSectionLayer layer = entry.getKey();
            MeshData mesh = entry.getValue();

            if (mesh != null && mesh.drawState().vertexCount() > 0) {
                ByteBuffer vertexBuf = mesh.vertexBuffer();
                ByteBuffer indexBuf = mesh.indexBuffer();

                int vertexSize = vertexBuf.remaining();
                int indexSize = indexBuf != null ? indexBuf.remaining() : 0;

                // Create custom buffers through Mojang's GpuDevice
                GpuBuffer vertexGpuBuf = device.createBuffer(
                        () -> "XenoVertexBuffer-" + sectionNode + "-" + layer.name(),
                        GpuBuffer.USAGE_VERTEX,
                        vertexSize
                );

                try (GpuBufferSlice.MappedView view = vertexGpuBuf.map(0, vertexSize, false, true)) {
                    MemoryIntrinsics.copy(vertexBuf, view.data(), vertexSize);
                }

                GpuBuffer indexGpuBuf = null;
                if (indexSize > 0 && indexBuf != null) {
                    indexGpuBuf = device.createBuffer(
                            () -> "XenoIndexBuffer-" + sectionNode + "-" + layer.name(),
                            GpuBuffer.USAGE_INDEX,
                            indexSize
                    );

                    try (GpuBufferSlice.MappedView view = indexGpuBuf.map(0, indexSize, false, true)) {
                        MemoryIntrinsics.copy(indexBuf, view.data(), indexSize);
                    }
                }

                // Store references on the custom CompiledSectionMesh object
                ((XenoMeshExtension) compiled).xeno$setBuffers(layer, vertexGpuBuf, indexGpuBuf);
            }
        }

        // Swap mesh reference and release old buffers automatically
        SectionMesh oldMesh = section.sectionMesh.getAndSet(compiled);
        if (oldMesh != null) {
            oldMesh.close();
        }
    }
}
