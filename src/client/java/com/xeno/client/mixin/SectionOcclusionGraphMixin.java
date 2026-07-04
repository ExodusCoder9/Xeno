package com.xeno.client.mixin;

import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingRequest;
import com.xeno.client.culling.CullingThread;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.util.VisibleForDebug;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
     * @author ExodusCoder9
     * @reason Delegates view area resets to the asynchronous Xeno culling thread.
     */
    @Overwrite
    public void waitAndReset(final @Nullable ViewArea viewArea) {
        this.xenoViewArea = viewArea;
        this.pendingPropagations.clear();
        if (this.xenoCullingThread != null) {
            this.xenoCullingThread.reset();
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Returns an empty set since chunk expectations are managed asynchronously.
     */
    @Overwrite
    public LongCollection expectedChunks() {
        return LongSets.EMPTY_SET;
    }

    /**
     * @author ExodusCoder9
     * @reason Triggers invalidation on the dedicated culling thread instead of the main thread.
     */
    @Overwrite
    public void invalidate() {
        if (this.xenoCullingThread != null) {
            this.xenoCullingThread.invalidate();
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Stubbed out as frustum invalidation is handled internally by the culling thread.
     */
    @Overwrite
    public void invalidateIfNeeded(final CameraRenderState camera, final int fov) {
    }

    /**
     * @author ExodusCoder9
     * @reason Injects pre-computed culling results directly from the background culling thread.
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
     * @author ExodusCoder9
     * @reason Checks the asynchronous culling thread for new frustum update results.
     */
    @Overwrite
    public boolean consumeFrustumUpdate() {
        if (this.xenoCullingThread == null) return false;
        return this.xenoCullingThread.consumeFrustumUpdate();
    }

    /**
     * @author ExodusCoder9
     * @reason Queues propagation tasks locally to be batch-submitted to the culling thread.
     */
    @Overwrite
    public void schedulePropagationFrom(final SectionRenderDispatcher.RenderSection section) {
        this.pendingPropagations.add(section);
    }

    /**
     * @author ExodusCoder9
     * @reason Submits a complete snapshot of the render state to the background culling thread.
     */
    @Overwrite
    public void update(final CameraRenderState camera, final int fov, final ChunkLoadingRenderState chunkLoadingRenderState) {
        if (this.xenoCullingThread == null || this.xenoViewArea == null) return;

        this.updateLoadedChunks(chunkLoadingRenderState.addedLoadedChunks, chunkLoadingRenderState.removedLoadedChunks);
        this.updateEmptySections(chunkLoadingRenderState.addedEmptySections, chunkLoadingRenderState.removedEmptySections);

        if (!camera.isFrustumCaptured) {
            RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage = ((ViewAreaAccessor) this.xenoViewArea).getSections();

            int minY = this.xenoViewArea.minSectionY();
            int maxY = this.xenoViewArea.maxSectionY();
            int viewDistance = this.xenoViewArea.getViewDistance();
            int sizeY = maxY - minY + 1;
            int sizeXZ = viewDistance * 2 + 1;

            SectionRenderDispatcher.RenderSection[] sectionArraySnapshot = new SectionRenderDispatcher.RenderSection[sizeXZ * sizeY * sizeXZ];
            for (SectionRenderDispatcher.RenderSection section : storage) {
                if (section != null) {
                    sectionArraySnapshot[section.index] = section;
                }
            }

            LongOpenHashSet emptySectionsSnapshot = this.emptySections.clone();
            LongOpenHashSet loadedChunksSnapshot = this.loadedChunks.clone();

            List<SectionRenderDispatcher.RenderSection> propagationsSnapshot = new ArrayList<>(this.pendingPropagations);
            this.pendingPropagations.clear();

            CullingRequest request = new CullingRequest(
                    camera.blockPos,
                    camera.pos,
                    camera.smartCull,
                    new Frustum(camera.cullFrustum),
                    fov,
                    this.xenoViewArea,
                    sectionArraySnapshot,
                    minY,
                    maxY,
                    sizeY,
                    sizeXZ,
                    viewDistance,
                    emptySectionsSnapshot,
                    loadedChunksSnapshot,
                    propagationsSnapshot
            );
            this.xenoCullingThread.submitRequest(request);
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Intercepts empty section updates to schedule propagations and update the local tracker.
     */
    @Overwrite
    public void updateEmptySections(final LongOpenHashSet added, final LongOpenHashSet removed) {
        this.emptySections.addAll(added);
        LongIterator iter = removed.longIterator();
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

    /**
     * @author ExodusCoder9
     * @reason Updates local tracking of loaded chunks for the asynchronous culling thread.
     */
    @Overwrite
    public void updateLoadedChunks(final LongOpenHashSet added, final LongOpenHashSet removed) {
        this.loadedChunks.addAll(added);
        this.loadedChunks.removeAll(removed);
    }

    /**
     * @author ExodusCoder9
     * @reason Fetches the dummy octree from the culling thread for debug rendering compatibility.
     */
    @Overwrite
    public @Nullable Octree getOctree() {
        if (this.xenoCullingThread == null) return null;
        return this.xenoCullingThread.getOctree();
    }

    /**
     * @author ExodusCoder9
     * @reason Stubbed to return null as node tracking is internal to the background culling thread.
     */
    @Overwrite
    @VisibleForDebug
    public SectionOcclusionGraph.Node getNode(final SectionRenderDispatcher.RenderSection section) {
        return null;
    }
}