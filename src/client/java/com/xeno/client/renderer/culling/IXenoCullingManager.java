package com.xeno.client.renderer.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import java.util.List;

/**
 * Interface defining the API contract for the Xeno Culling Manager.
 */
public interface IXenoCullingManager {
    void waitAndReset(ViewArea viewArea);
    void invalidate();
    void schedulePropagationFrom(SectionRenderDispatcher.RenderSection section);
    void update(CameraRenderState camera, int fov, ChunkLoadingRenderState chunkLoading, LongOpenHashSet emptySections, LongOpenHashSet loadedChunks);
    void addSectionsInFrustum(Frustum frustum, List<SectionRenderDispatcher.RenderSection> visible, List<SectionRenderDispatcher.RenderSection> nearbyVisible);
    boolean consumeFrustumUpdate();
    Octree getOctree();
    void destroy();
}
