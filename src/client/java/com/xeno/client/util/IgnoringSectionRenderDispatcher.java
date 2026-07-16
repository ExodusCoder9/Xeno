package com.xeno.client.util;

import java.util.function.Consumer;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class IgnoringSectionRenderDispatcher extends SectionRenderDispatcher {
    public IgnoringSectionRenderDispatcher(
            TracingExecutor executor,
            RenderBuffers renderBuffers,
            @Nullable SectionCompiler sectionCompiler,
            Consumer<RenderSection> onSectionMeshUpdate
    ) {
        super(executor, renderBuffers, sectionCompiler, onSectionMeshUpdate);
        super.dispose();
    }

    @Override
    public void setCompiler(@NonNull SectionCompiler sectionCompiler) {}

    @Override
    public void setCameraPosition(@NonNull Vec3 cameraPosition) {}

    @Override
    public @Nullable RenderSectionBufferSlice getRenderSectionSlice(@NonNull SectionMesh sectionMesh, @NonNull ChunkSectionLayer layer) {
        return null;
    }

    @Override
    public void lock() {}

    @Override
    public void unlock() {}

    @Override
    public void uploadTerrainBuffersToGpu() {}

    @Override
    public void clearCompileQueue() {}

    @Override
    public boolean isQueueEmpty() {
        return true;
    }

    @Override
    public void dispose() {}

    @Override
    public @NonNull String getStats() {
        return "None";
    }

    @Override
    public int getCompileQueueSize() {
        return 0;
    }

    @Override
    public int getFreeBufferCount() {
        return 0;
    }
}
