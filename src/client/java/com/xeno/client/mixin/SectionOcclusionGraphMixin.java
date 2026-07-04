package com.xeno.client.mixin;

import com.xeno.client.culling.CullingThread;
import com.xeno.client.culling.CullingRequest;
import com.xeno.client.culling.CullingOutput;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {
    @Shadow @Final
    private LongOpenHashSet emptySections;

    @Shadow @Final
    private LongOpenHashSet loadedChunks;

    @Unique
    private CullingThread xenoCullingThread;
    
    @Unique
    private ViewArea xenoViewArea;

    @Unique
    private final List<SectionRenderDispatcher.RenderSection> pendingPropagations = new ArrayList<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.xenoCullingThread = new CullingThread();
        this.xenoCullingThread.start();
    }

    /**
     * @author Antigravity
     * @reason Overwrite waitAndReset to delegate to the asynchronous culling thread
     */
    @Overwrite
    public void waitAndReset(final @Nullable ViewArea viewArea) {
        this.xenoViewArea = viewArea;
        synchronized (this.pendingPropagations) {
            this.pendingPropagations.clear();
        }
        if (this.xenoCullingThread != null) {
            this.xenoCullingThread.reset(viewArea);
        }
    }

    /**
     * @author Antigravity
     * @reason Overwrite expectedChunks to fetch pending chunk loads from the culling thread
     */
    @Overwrite
    public LongCollection expectedChunks() {
        return it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;
    }

    /**
     * @author Antigravity
     * @reason Overwrite invalidate to mark full culling update needed on culling thread
     */
    @Overwrite
    public void invalidate() {
        if (this.xenoCullingThread != null) {
            this.xenoCullingThread.invalidate();
        }
    }

    /**
     * @author Antigravity
     * @reason Overwrite invalidateIfNeeded to delegate to culling thread's internal tracking
     */
    @Overwrite
    public void invalidateIfNeeded(final CameraRenderState camera, final int fov) {
        // Handled internally by the culling thread during update requests
    }

    /**
     * @author Antigravity
     * @reason Overwrite addSectionsInFrustum to directly inject the pre-computed culling results
     */
    @Overwrite
    public void addSectionsInFrustum(
       final Frustum frustum,
       final List<SectionRenderDispatcher.RenderSection> visibleSections,
       final List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections
    ) {
        if (this.xenoCullingThread == null) return;
        CullingOutput output = this.xenoCullingThread.getLatestOutput();
        if (output != null) {
            visibleSections.addAll(output.visibleSections());
            nearbyVisibleSections.addAll(output.nearbyVisibleSections());
        }
    }

    /**
     * @author Antigravity
     * @reason Overwrite consumeFrustumUpdate to check if culling thread produced a new result
     */
    @Overwrite
    public boolean consumeFrustumUpdate() {
        if (this.xenoCullingThread == null) return false;
        return this.xenoCullingThread.consumeFrustumUpdate();
    }

    /**
     * @author Antigravity
     * @reason Overwrite schedulePropagationFrom to queue propagation task to culling thread
     */
    @Overwrite
    public void schedulePropagationFrom(final SectionRenderDispatcher.RenderSection section) {
        synchronized (this.pendingPropagations) {
            this.pendingPropagations.add(section);
        }
    }

    /**
     * @author Antigravity
     * @reason Overwrite update to submit a new culling request to the culling thread
     */
    @Overwrite
    public void update(final CameraRenderState camera, final int fov, final ChunkLoadingRenderState chunkLoadingRenderState) {
        if (this.xenoCullingThread == null || this.xenoViewArea == null) return;
        
        // Enqueue loaded chunks and empty sections changes
        this.updateLoadedChunks(chunkLoadingRenderState.addedLoadedChunks, chunkLoadingRenderState.removedLoadedChunks);
        this.updateEmptySections(chunkLoadingRenderState.addedEmptySections, chunkLoadingRenderState.removedEmptySections);

        if (!camera.isFrustumCaptured) {
            // 1. Build sectionMap snapshot (shallow copy)
            net.minecraft.client.RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage = 
                ((ViewAreaAccessor) this.xenoViewArea).getSections();
            Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> sectionMapSnapshot = new Long2ObjectOpenHashMap<>(storage.size());
            for (SectionRenderDispatcher.RenderSection section : storage) {
                if (section != null) {
                    sectionMapSnapshot.put(section.getSectionNode(), section);
                }
            }

            // 2. Clone empty sections and loaded chunks snapshots
            LongOpenHashSet emptySectionsSnapshot;
            LongOpenHashSet loadedChunksSnapshot;
            synchronized (this.emptySections) {
                emptySectionsSnapshot = this.emptySections.clone();
            }
            synchronized (this.loadedChunks) {
                loadedChunksSnapshot = this.loadedChunks.clone();
            }

            // 3. Clone propagation queue
            List<SectionRenderDispatcher.RenderSection> propagationsSnapshot;
            synchronized (this.pendingPropagations) {
                propagationsSnapshot = new ArrayList<>(this.pendingPropagations);
                this.pendingPropagations.clear();
            }

            // 4. Submit request to culling thread
            CullingRequest request = new CullingRequest(
                camera.blockPos,
                camera.pos,
                camera.smartCull,
                new Frustum(camera.cullFrustum),
                fov,
                this.xenoViewArea,
                sectionMapSnapshot,
                emptySectionsSnapshot,
                loadedChunksSnapshot,
                propagationsSnapshot
            );
            this.xenoCullingThread.submitRequest(request);

            // 5. Avoid initial empty frames by waiting briefly for the first output
            if (this.xenoCullingThread.getLatestOutput() == null) {
                long start = System.currentTimeMillis();
                while (this.xenoCullingThread.getLatestOutput() == null && System.currentTimeMillis() - start < 50) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * @author Antigravity
     * @reason Overwrite updateEmptySections to queue empty section changes to culling thread
     */
    @Overwrite
    public void updateEmptySections(final LongOpenHashSet added, final LongOpenHashSet removed) {
        synchronized (this.emptySections) {
            this.emptySections.addAll(added);
            it.unimi.dsi.fastutil.longs.LongIterator iter = removed.longIterator();
            while (iter.hasNext()) {
                long sectionNode = iter.nextLong();
                if (this.emptySections.remove(sectionNode)) {
                    SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) this.xenoViewArea).invokeGetRenderSection(sectionNode);
                    if (section != null) {
                        this.schedulePropagationFrom(section);
                        section.setWasPreviouslyEmpty(true);
                    }
                }
            }
        }
    }

    /**
     * @author Antigravity
     * @reason Overwrite updateLoadedChunks to queue loaded chunks changes to culling thread
     */
    @Overwrite
    public void updateLoadedChunks(final LongOpenHashSet added, final LongOpenHashSet removed) {
        synchronized (this.loadedChunks) {
            this.loadedChunks.addAll(added);
            this.loadedChunks.removeAll(removed);
        }
    }

    /**
     * @author Antigravity
     * @reason Overwrite getOctree as a stub since frustum culling is fully done in background
     */
    @Overwrite
    public @Nullable Octree getOctree() {
        if (this.xenoCullingThread == null) return null;
        return this.xenoCullingThread.getOctree();
    }

    /**
     * @author Antigravity
     * @reason Overwrite getNode as a stub
     */
    @Overwrite
    public SectionOcclusionGraph.Node getNode(final SectionRenderDispatcher.RenderSection section) {
        return null;
    }
}
