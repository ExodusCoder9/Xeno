package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.logging.LogUtils;
import com.xeno.client.XenoClient;
import com.xeno.client.renderer.memory.MemoryIntrinsics;
import com.xeno.client.renderer.memory.XenoBufferPool;
import com.xeno.client.renderer.util.XenoMeshExtension;
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
                break;
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

        float[] quadCenters = null;
        int quadCount = 0;

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

                com.xeno.client.renderer.draw.XenoUniformBinder binder = new com.xeno.client.renderer.draw.XenoUniformBinder();
                int baseVertex = (int)(vertexAlloc.offset / layer.pipeline().getVertexFormatBinding(0).getVertexSize());
                net.minecraft.client.renderer.chunk.SectionMesh.SectionDraw sectionDraw = compiled.getSectionDraw(layer);
                int indexCount = sectionDraw.indexCount();
                
                GpuBuffer indexBuffer = null;
                com.mojang.blaze3d.IndexType indexType = null;
                int firstIndex = 0;
                if (indexAlloc != null) {
                    indexBuffer = indexAlloc.buffer;
                    indexType = sectionDraw.indexType();
                    firstIndex = (int)(indexAlloc.offset / indexType.bytes);
                }
                
                com.mojang.blaze3d.systems.RenderPass.Draw<GpuBufferSlice[]> cachedDraw = new com.mojang.blaze3d.systems.RenderPass.Draw<>(
                    0, vertexAlloc.buffer, indexBuffer, indexType, firstIndex, indexCount, baseVertex, binder
                );
                
                ((XenoMeshExtension) compiled).xeno$setCachedDraw(layer, cachedDraw);
                ((XenoMeshExtension) compiled).xeno$setUniformBinder(layer, binder);

                if (layer == ChunkSectionLayer.TRANSLUCENT) {
                    quadCount = vertexSize / (4 * 28);
                    quadCenters = new float[quadCount * 3];
                    for (int i = 0; i < quadCount; i++) {
                        int v0 = i * 4 * 28;
                        int v1 = (i * 4 + 1) * 28;
                        int v2 = (i * 4 + 2) * 28;
                        int v3 = (i * 4 + 3) * 28;

                        float x0 = vertexBuf.getFloat(v0);
                        float y0 = vertexBuf.getFloat(v0 + 4);
                        float z0 = vertexBuf.getFloat(v0 + 8);

                        float x1 = vertexBuf.getFloat(v1);
                        float y1 = vertexBuf.getFloat(v1 + 4);
                        float z1 = vertexBuf.getFloat(v1 + 8);

                        float x2 = vertexBuf.getFloat(v2);
                        float y2 = vertexBuf.getFloat(v2 + 4);
                        float z2 = vertexBuf.getFloat(v2 + 8);

                        float x3 = vertexBuf.getFloat(v3);
                        float y3 = vertexBuf.getFloat(v3 + 4);
                        float z3 = vertexBuf.getFloat(v3 + 8);

                        quadCenters[i * 3] = (x0 + x1 + x2 + x3) / 4.0f;
                        quadCenters[i * 3 + 1] = (y0 + y1 + y2 + y3) / 4.0f;
                        quadCenters[i * 3 + 2] = (z0 + z1 + z2 + z3) / 4.0f;
                    }
                }
            }
        }

        if (quadCenters != null) {
            ((XenoMeshExtension) compiled).xeno$setTranslucentData(quadCenters, quadCount);
        }

        SectionMesh oldMesh = section.sectionMesh.getAndSet(compiled);
        if (oldMesh != null) {
            oldMesh.close();
        }
    }
}
