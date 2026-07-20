package com.xeno.client.renderer.dispatcher;

import com.xeno.client.XenoClient;
import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.memory.XenoBufferPool;
import com.xeno.client.renderer.sorting.TranslucentSorter;
import com.xeno.client.renderer.util.XenoMeshExtension;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Custom Section Renderer that implements IXenoSectionRenderer.
 * It manages asynchronous mesh uploads and buffer pools using Xeno's allocator systems.
 */
public class XenoSectionRenderer implements IXenoSectionRenderer {

    public static class UploadTask {
        public final SectionRenderDispatcher.RenderSection section;
        public final SectionCompiler.Results results;
        public final SectionBufferBuilderPack builders;

        public UploadTask(SectionRenderDispatcher.RenderSection section, SectionCompiler.Results results, SectionBufferBuilderPack builders) {
            this.section = section;
            this.results = results;
            this.builders = builders;
        }
    }

    private final Queue<UploadTask> uploadQueue = new ConcurrentLinkedQueue<>();
    private final Queue<SectionBufferBuilderPack> packPool = new ConcurrentLinkedQueue<>();
    private final Consumer<SectionRenderDispatcher.RenderSection> onSectionMeshUpdate;
    private SectionCompiler compiler;

    public XenoSectionRenderer(SectionCompiler compiler, Consumer<SectionRenderDispatcher.RenderSection> onSectionMeshUpdate) {
        this.compiler = compiler;
        this.onSectionMeshUpdate = onSectionMeshUpdate;
    }

    @Override
    public void setCompiler(SectionCompiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public SectionCompiler getCompiler() {
        return this.compiler;
    }

    @Override
    public SectionRenderDispatcher.@Nullable RenderSectionBufferSlice getRenderSectionSlice(@NonNull SectionMesh sectionMesh, @NonNull ChunkSectionLayer layer) {
        if (sectionMesh instanceof XenoMeshExtension ext) {
            XenoBufferPool.Allocation vertexAlloc = ext.xeno$getVertexAllocation(layer);
            XenoBufferPool.Allocation indexAlloc = ext.xeno$getIndexAllocation(layer);
            if (vertexAlloc != null) {
                return new SectionRenderDispatcher.RenderSectionBufferSlice(
                        vertexAlloc.buffer, vertexAlloc.offset,
                        indexAlloc != null ? indexAlloc.buffer : null, indexAlloc != null ? indexAlloc.offset : 0L
                );
            }
        }
        return null;
    }

    @Override
    public void uploadTerrainBuffersToGpu() {
        XenoWorldRenderer.tickFrame();

        UploadTask task;
        while ((task = this.uploadQueue.poll()) != null) {
            XenoWorldRenderer.uploadToGpu(task.section, task.results);
            if (this.onSectionMeshUpdate != null) {
                this.onSectionMeshUpdate.accept(task.section);
            }
            this.releasePack(task.builders);
        }
    }

    @Override
    public void clearCompileQueue() {
        this.uploadQueue.clear();
    }

    @Override
    public boolean isQueueEmpty() {
        return this.uploadQueue.isEmpty();
    }

    @Override
    public void dispose() {
        this.clearCompileQueue();
        SectionBufferBuilderPack pack;
        while ((pack = this.packPool.poll()) != null) {
            pack.close();
        }
    }

    @Override
    public @NonNull String getStats() {
        return "Xeno Pipeline Active (Phase 2 Pool)";
    }

    @Override
    public int getCompileQueueSize() {
        return this.uploadQueue.size();
    }

    @Override
    public int getFreeBufferCount() {
        return this.packPool.size();
    }

    @Override
    public void setCameraPosition(Vec3 cameraPosition) {
        // No-op or tracked if needed
    }

    @Override
    public SectionBufferBuilderPack acquirePack() {
        SectionBufferBuilderPack pack = this.packPool.poll();
        if (pack == null) {
            pack = new SectionBufferBuilderPack();
        }
        return pack;
    }

    @Override
    public void releasePack(SectionBufferBuilderPack pack) {
        if (pack != null) {
            pack.discardAll();
            this.packPool.offer(pack);
        }
    }

    @Override
    public void queueUpload(SectionRenderDispatcher.RenderSection section, SectionCompiler.Results results, SectionBufferBuilderPack builders) {
        this.uploadQueue.add(new UploadTask(section, results, builders));
    }

    @Override
    public void compileSectionSync(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region) {
        if (region == null) return;
        SectionCompiler comp = this.getCompiler();
        if (comp == null) return;

        SectionPos sectionPos = SectionPos.of(section.getSectionNode());
        
        // Acquire builder pack from dispatcher's synchronized pool
        SectionBufferBuilderPack builders = this.acquirePack();
        builders.discardAll(); // Silently reset builders to start fresh without warnings

        SectionCompiler.Results results = null;
        try {
            Vec3 cameraPos = XenoClient.getCameraPos();
            float rx = 0;
            float ry = 0;
            float rz = 0;
            if (cameraPos != null) {
                BlockPos origin = section.getRenderOrigin();
                rx = (float) (cameraPos.x - origin.getX());
                ry = (float) (cameraPos.y - origin.getY());
                rz = (float) (cameraPos.z - origin.getZ());
            }
            VertexSorting vertexSorting = VertexSorting.byDistance(rx, ry, rz);

            results = comp.compile(sectionPos, region, vertexSorting, builders);
        } catch (Throwable t) {
            // Silently absorb exceptions during reload as the region is invalidated
        } finally {
            if (results != null) {
                this.queueUpload(section, results, builders);
            } else {
                // If compilation failed/cancelled, safely return the builders pack back to the pool
                this.releasePack(builders);
            }
        }
    }

    @Override
    public void compileSectionAsync(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region) {
        CompletableFuture.runAsync(() -> {
            this.compileSectionSync(section, region);
        }, Util.backgroundExecutor());
    }

    @Override
    public void resortTransparency(SectionRenderDispatcher.RenderSection section) {
        SectionMesh mesh = section.getSectionMesh();
        if (mesh instanceof CompiledSectionMesh compiled && mesh instanceof XenoMeshExtension ext) {
            XenoBufferPool.Allocation indexAlloc = ext.xeno$getIndexAllocation(ChunkSectionLayer.TRANSLUCENT);
            TranslucentSorter.resort(compiled, ext, section.getSectionNode(), section.getRenderOrigin(), indexAlloc);
        }
    }
}
