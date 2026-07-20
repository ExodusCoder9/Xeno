package com.xeno.client.renderer.dispatcher;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;

/**
 * Defines the custom behavior for SectionRenderDispatcher delegation.
 */
public interface IXenoSectionRenderer {
    void setCompiler(SectionCompiler compiler);
    SectionCompiler getCompiler();
    SectionRenderDispatcher.RenderSectionBufferSlice getRenderSectionSlice(SectionMesh sectionMesh, ChunkSectionLayer layer);
    void uploadTerrainBuffersToGpu();
    void clearCompileQueue();
    boolean isQueueEmpty();
    void dispose();
    String getStats();
    int getCompileQueueSize();
    int getFreeBufferCount();
    void setCameraPosition(Vec3 cameraPosition);

    SectionBufferBuilderPack acquirePack();
    void releasePack(SectionBufferBuilderPack pack);
    void queueUpload(SectionRenderDispatcher.RenderSection section, SectionCompiler.Results results, SectionBufferBuilderPack builders);

    // Compilation and Sorting delegates
    void compileSectionSync(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region);
    void compileSectionAsync(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region);
    void resortTransparency(SectionRenderDispatcher.RenderSection section);
}
