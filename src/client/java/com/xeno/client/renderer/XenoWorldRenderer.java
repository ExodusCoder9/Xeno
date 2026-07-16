package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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

import java.nio.ByteBuffer;
import java.util.Map;

public final class XenoWorldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable XenoWorldRenderer instance;

    private static XenoBufferPool vertexBufferPool;
    private static XenoBufferPool indexBufferPool;

    private int compileSectionsSkipped;
    private int compileSectionsProcessed;
    private int framesSinceInit;
    private boolean active;

    public XenoWorldRenderer() {
        this.active = true;
        initPools();
        LOGGER.info("[Xeno] World renderer initialized");
    }

    public static void setInstance(@Nullable XenoWorldRenderer renderer) {
        instance = renderer;
    }

    public static @Nullable XenoWorldRenderer getInstance() {
        return instance;
    }

    public static void initPools() {
        if (vertexBufferPool == null) {
            // Allocate 128MB Vertex Buffer Pool
            vertexBufferPool = new XenoBufferPool(
                    "XenoVertexPool",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    128 * 1024 * 1024L
            );
        }
        if (indexBufferPool == null) {
            // Allocate 32MB Index Buffer Pool
            indexBufferPool = new XenoBufferPool(
                    "XenoIndexPool",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_MAP_WRITE,
                    32 * 1024 * 1024L
            );
        }
    }

    public static void destroyPools() {
        if (vertexBufferPool != null) {
            vertexBufferPool.close();
            vertexBufferPool = null;
        }
        if (indexBufferPool != null) {
            indexBufferPool.close();
            indexBufferPool = null;
        }
    }

    public static void freeAllocations(
            Map<ChunkSectionLayer, XenoBufferPool.Allocation> vertexAllocations,
            Map<ChunkSectionLayer, XenoBufferPool.Allocation> indexAllocations
    ) {
        if (vertexBufferPool != null) {
            for (XenoBufferPool.Allocation alloc : vertexAllocations.values()) {
                vertexBufferPool.free(alloc);
            }
        }
        vertexAllocations.clear();

        if (indexBufferPool != null) {
            for (XenoBufferPool.Allocation alloc : indexAllocations.values()) {
                indexBufferPool.free(alloc);
            }
        }
        indexAllocations.clear();
    }

    public void reload() {
        LOGGER.info("[Xeno] World renderer reloading");
        destroyPools();
        initPools();
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
        destroyPools();
        this.compileSectionsSkipped = 0;
        this.compileSectionsProcessed = 0;
        this.framesSinceInit = 0;
        LOGGER.info("[Xeno] World renderer destroyed");
    }

    /**
     * Uploads the compiled mesh results to unified GpuBuffer pools at specific byte offsets.
     */
    public static void uploadToGpu(SectionRenderDispatcher.RenderSection section, net.minecraft.client.renderer.chunk.SectionCompiler.Results results) {
        initPools(); // Safety check

        Vec3 cameraPos = XenoClient.getCameraPos();
        long sectionNode = section.getSectionNode();
        TranslucencyPointOfView pointOfView = TranslucencyPointOfView.of(cameraPos != null ? cameraPos : Vec3.ZERO, sectionNode);
        CompiledSectionMesh compiled = new CompiledSectionMesh(pointOfView, results);

        for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
            ChunkSectionLayer layer = entry.getKey();
            MeshData mesh = entry.getValue();

            if (mesh != null && mesh.drawState().vertexCount() > 0) {
                ByteBuffer vertexBuf = mesh.vertexBuffer();
                ByteBuffer indexBuf = mesh.indexBuffer();

                int vertexSize = vertexBuf.remaining();
                int indexSize = indexBuf != null ? indexBuf.remaining() : 0;

                // Request allocations from our unified pools
                XenoBufferPool.Allocation vertexAlloc = vertexBufferPool.allocate(vertexSize);
                XenoBufferPool.Allocation indexAlloc = null;

                // Map and copy vertex data at the specific offset of the Mega VBO
                try (GpuBufferSlice.MappedView view = vertexAlloc.buffer.map(vertexAlloc.offset, vertexSize, false, true)) {
                    MemoryIntrinsics.copy(vertexBuf, view.data(), vertexSize);
                }

                if (indexSize > 0 && indexBuf != null) {
                    indexAlloc = indexBufferPool.allocate(indexSize);
                    // Map and copy index data at the specific offset of the Mega IBO
                    try (GpuBufferSlice.MappedView view = indexAlloc.buffer.map(indexAlloc.offset, indexSize, false, true)) {
                        MemoryIntrinsics.copy(indexBuf, view.data(), indexSize);
                    }
                }

                // Attach pool allocations to compiled mesh
                ((XenoMeshExtension) compiled).xeno$setAllocations(layer, vertexAlloc, indexAlloc);
            }
        }

        // Swap mesh reference and release old allocations
        SectionMesh oldMesh = section.sectionMesh.getAndSet(compiled);
        if (oldMesh != null) {
            oldMesh.close();
        }
    }
}
