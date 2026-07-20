package com.xeno.client.renderer;

import com.xeno.client.renderer.util.XenoMeshExtension;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class XenoSectionRenderDispatcher extends SectionRenderDispatcher {
    
    public static class UploadTask {
        public final RenderSection section;
        public final SectionCompiler.Results results;
        public final SectionBufferBuilderPack builders;
        
        public UploadTask(RenderSection section, SectionCompiler.Results results, SectionBufferBuilderPack builders) {
            this.section = section;
            this.results = results;
            this.builders = builders;
        }
    }
    
    private final Queue<UploadTask> uploadQueue = new ConcurrentLinkedQueue<>();
    private final Queue<SectionBufferBuilderPack> packPool = new ConcurrentLinkedQueue<>();
    private final SectionCompiler compiler;
    private final Consumer<RenderSection> onSectionMeshUpdate;

    public XenoSectionRenderDispatcher(
            TracingExecutor executor,
            RenderBuffers renderBuffers,
            SectionCompiler sectionCompiler,
            Consumer<RenderSection> onSectionMeshUpdate
    ) {
        super(executor, renderBuffers, sectionCompiler, onSectionMeshUpdate);
        this.compiler = sectionCompiler;
        this.onSectionMeshUpdate = onSectionMeshUpdate;
        super.dispose();
    }
    
    public SectionCompiler getCompiler() {
        return this.compiler;
    }
    
    public Queue<UploadTask> getUploadQueue() {
        return this.uploadQueue;
    }

    public SectionBufferBuilderPack acquirePack() {
        SectionBufferBuilderPack pack = this.packPool.poll();
        if (pack == null) {
            pack = new SectionBufferBuilderPack();
        }
        return pack;
    }

    public void releasePack(SectionBufferBuilderPack pack) {
        if (pack != null) {
            pack.discardAll();
            this.packPool.offer(pack);
        }
    }

    @Override
    public @Nullable RenderSectionBufferSlice getRenderSectionSlice(@NonNull SectionMesh sectionMesh, @NonNull ChunkSectionLayer layer) {
        if (sectionMesh instanceof XenoMeshExtension ext) {
            XenoBufferPool.Allocation vertexAlloc = ext.xeno$getVertexAllocation(layer);
            XenoBufferPool.Allocation indexAlloc = ext.xeno$getIndexAllocation(layer);
            if (vertexAlloc != null) {
                return new RenderSectionBufferSlice(
                        vertexAlloc.buffer, vertexAlloc.offset,
                        indexAlloc != null ? indexAlloc.buffer : null, indexAlloc != null ? indexAlloc.offset : 0L
                );
            }
        }
        return null;
    }

    @Override
    public void uploadTerrainBuffersToGpu() {
        // Increment the frame counter and process safe deferred frees
        XenoWorldRenderer.tickFrame();

        UploadTask task;
        while ((task = this.uploadQueue.poll()) != null) {
            XenoWorldRenderer.uploadToGpu(task.section, task.results);
            this.onSectionMeshUpdate.accept(task.section);
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
}
