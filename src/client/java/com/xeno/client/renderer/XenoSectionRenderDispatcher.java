package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.xeno.client.util.XenoMeshExtension;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.RenderBuffers;
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
        
        public UploadTask(RenderSection section, SectionCompiler.Results results) {
            this.section = section;
            this.results = results;
        }
    }
    
    private final Queue<UploadTask> uploadQueue = new ConcurrentLinkedQueue<>();
    private final SectionCompiler compiler;

    public XenoSectionRenderDispatcher(
            TracingExecutor executor,
            RenderBuffers renderBuffers,
            SectionCompiler sectionCompiler,
            Consumer<RenderSection> onSectionMeshUpdate
    ) {
        super(executor, renderBuffers, sectionCompiler, onSectionMeshUpdate);
        this.compiler = sectionCompiler;
        // Shut down vanilla's compilation queue and release its resources
        super.dispose();
    }
    
    public SectionCompiler getCompiler() {
        return this.compiler;
    }
    
    public Queue<UploadTask> getUploadQueue() {
        return this.uploadQueue;
    }

    @Override
    public @Nullable RenderSectionBufferSlice getRenderSectionSlice(@NonNull SectionMesh sectionMesh, @NonNull ChunkSectionLayer layer) {
        if (sectionMesh instanceof XenoMeshExtension ext) {
            GpuBuffer vertexBuffer = ext.xeno$getVertexBuffer(layer);
            GpuBuffer indexBuffer = ext.xeno$getIndexBuffer(layer);
            if (vertexBuffer != null) {
                return new RenderSectionBufferSlice(
                        vertexBuffer, 0L,
                        indexBuffer, 0L
                );
            }
        }
        return null;
    }

    @Override
    public void uploadTerrainBuffersToGpu() {
        UploadTask task;
        while ((task = this.uploadQueue.poll()) != null) {
            XenoWorldRenderer.uploadToGpu(task.section, task.results);
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
    }

    @Override
    public @NonNull String getStats() {
        return "Xeno Pipeline Active";
    }

    @Override
    public int getCompileQueueSize() {
        return this.uploadQueue.size();
    }
}
