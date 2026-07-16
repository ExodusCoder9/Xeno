package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class XenoWorldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable XenoWorldRenderer instance;

    private static XenoBufferPool vertexBufferPool;
    private static XenoBufferPool indexBufferPool;

    private static int currentFrame = 0;

    private static class DeferredFree {
        public final XenoBufferPool.Allocation alloc;
        public final int frameNumber;
        public final boolean isIndex;

        public DeferredFree(XenoBufferPool.Allocation alloc, int frameNumber, boolean isIndex) {
            this.alloc = alloc;
            this.frameNumber = frameNumber;
            this.isIndex = isIndex;
        }
    }

    private static final Queue<DeferredFree> deferredFrees = new ConcurrentLinkedQueue<>();

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
        deferredFrees.clear();
        if (vertexBufferPool != null) {
            vertexBufferPool.close();
            vertexBufferPool = null;
        }
        if (indexBufferPool != null) {
            indexBufferPool.close();
            indexBufferPool = null;
        }
    }

    public static synchronized void tickFrame() {
        currentFrame++;

        // Process allocations that have been abandoned for at least 3 frames
        DeferredFree df;
        while ((df = deferredFrees.peek()) != null) {
            if (currentFrame - df.frameNumber >= 3) {
                deferredFrees.poll();
                if (df.isIndex) {
                    if (indexBufferPool != null) {
                        indexBufferPool.free(df.alloc);
                    }
                } else {
                    if (vertexBufferPool != null) {
                        vertexBufferPool.free(df.alloc);
                    }
                }
            } else {
                break; // Since queue is ordered, the rest are also not ready yet
            }
        }
    }

    public static boolean isPoolBuffer(GpuBuffer buffer) {
        return vertexBufferPool != null && vertexBufferPool.containsBuffer(buffer);
    }

    public static void freeAllocations(
            Map<ChunkSectionLayer, XenoBufferPool.Allocation> vertexAllocations,
            Map<ChunkSectionLayer, XenoBufferPool.Allocation> indexAllocations
    ) {
        // Enqueue the old allocations for deferred freeing instead of freeing them immediately
        for (XenoBufferPool.Allocation alloc : vertexAllocations.values()) {
            deferredFrees.add(new DeferredFree(alloc, currentFrame, false));
        }
        vertexAllocations.clear();

        for (XenoBufferPool.Allocation alloc : indexAllocations.values()) {
            deferredFrees.add(new DeferredFree(alloc, currentFrame, true));
        }
        indexAllocations.clear();
    }

    public void reload() {
        LOGGER.info("[Xeno] World renderer reloading");
        initPools();
        tickFrame();
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

                XenoBufferPool.Allocation vertexAlloc = vertexBufferPool.allocate(vertexSize);
                XenoBufferPool.Allocation indexAlloc = null;

                try (GpuBufferSlice.MappedView view = vertexAlloc.buffer.map(vertexAlloc.offset, vertexSize, false, true)) {
                    MemoryIntrinsics.copy(vertexBuf, view.data(), vertexSize);
                }

                if (indexSize > 0 && indexBuf != null) {
                    indexAlloc = indexBufferPool.allocate(indexSize);
                    try (GpuBufferSlice.MappedView view = indexAlloc.buffer.map(indexAlloc.offset, indexSize, false, true)) {
                        MemoryIntrinsics.copy(indexBuf, view.data(), indexSize);
                    }
                }

                ((XenoMeshExtension) compiled).xeno$setAllocations(layer, vertexAlloc, indexAlloc);
            }
        }

        SectionMesh oldMesh = section.sectionMesh.getAndSet(compiled);
        if (oldMesh != null) {
            oldMesh.close();
        }
    }
}
