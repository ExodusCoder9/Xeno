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
import net.minecraft.util.VisibleForDebug;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongCollection;
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
     * @author ExodusCoder9
     * @reason Overwrite waitAndReset to delegate to the asynchronous culling thread
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
     * @reason Overwrite expectedChunks to return an empty collection as full updates happen on background thread
     */
    @Overwrite
    public LongCollection expectedChunks() {
        return it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET;
    }

    /**
     * @author ExodusCoder9
     * @reason Overwrite invalidate to mark full culling update needed on culling thread
     */
    @Overwrite
    public void invalidate() {
        if (this.xenoCullingThread != null) {
            this.xenoCullingThread.invalidate();
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Overwrite invalidateIfNeeded to delegate to culling thread's internal tracking
     */
    @Overwrite
    public void invalidateIfNeeded(final CameraRenderState camera, final int fov) {
        // Handled internally by the culling thread during update requests
    }

    /**
     * @author ExodusCoder9
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
     * @author ExodusCoder9
     * @reason Overwrite consumeFrustumUpdate to check if culling thread produced a new result
     */
    @Overwrite
    public boolean consumeFrustumUpdate() {
        if (this.xenoCullingThread == null) return false;
        return this.xenoCullingThread.consumeFrustumUpdate();
    }

    /**
     * @author ExodusCoder9
     * @reason Overwrite schedulePropagationFrom to queue propagation task to culling thread
     */
    @Overwrite
    public void schedulePropagationFrom(final SectionRenderDispatcher.RenderSection section) {
        this.pendingPropagations.add(section);
    }

    /**
     * @author ExodusCoder9
     * @reason Overwrite update to submit a new culling request to the culling thread
     */
    @Overwrite
    public void update(final CameraRenderState camera, final int fov, final ChunkLoadingRenderState chunkLoadingRenderState) {
        if (this.xenoCullingThread == null || this.xenoViewArea == null) return;
        
        // Enqueue loaded chunks and empty sections changes
        this.updateLoadedChunks(chunkLoadingRenderState.addedLoadedChunks, chunkLoadingRenderState.removedLoadedChunks);
        this.updateEmptySections(chunkLoadingRenderState.addedEmptySections, chunkLoadingRenderState.removedEmptySections);

        if (!camera.isFrustumCaptured) {
            // 1. Build section snapshot array
            net.minecraft.client.RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage = 
                ((ViewAreaAccessor) this.xenoViewArea).getSections();
            RotatingSectionStorageExt storageExt = (RotatingSectionStorageExt) (Object) storage;
            SectionRenderDispatcher.RenderSection[] sectionArraySnapshot = 
                (SectionRenderDispatcher.RenderSection[]) (Object) storageExt.xenoGetValues();
                
            int minY = this.xenoViewArea.minSectionY();
            int maxY = this.xenoViewArea.maxSectionY();
            int sizeY = storageExt.xenoGetGridSizeY();
            int sizeXZ = storageExt.xenoGetGridSizeXZ();
            int viewDistance = this.xenoViewArea.getViewDistance();

            // 2. Clone empty sections and loaded chunks snapshots without synchronization
            LongOpenHashSet emptySectionsSnapshot = this.emptySections.clone();
            LongOpenHashSet loadedChunksSnapshot = this.loadedChunks.clone();

            // 3. Clone propagation queue without synchronization
            List<SectionRenderDispatcher.RenderSection> propagationsSnapshot = new ArrayList<>(this.pendingPropagations);
            this.pendingPropagations.clear();

            // 4. Submit request to culling thread
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
     * @reason Overwrite updateEmptySections to queue empty section changes to culling thread
     */
    @Overwrite
    public void updateEmptySections(final LongOpenHashSet added, final LongOpenHashSet removed) {
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

    /**
     * @author ExodusCoder9
     * @reason Overwrite updateLoadedChunks to queue loaded chunks changes to culling thread
     */
    @Overwrite
    public void updateLoadedChunks(final LongOpenHashSet added, final LongOpenHashSet removed) {
        this.loadedChunks.addAll(added);
        this.loadedChunks.removeAll(removed);
    }

    /**
     * @author ExodusCoder9
     * @reason Overwrite getOctree as a stub since frustum culling is fully done in background
     */
    @Overwrite
    public @Nullable Octree getOctree() {
        if (this.xenoCullingThread == null) return null;
        return this.xenoCullingThread.getOctree();
    }

    /**
     * @author ExodusCoder9
     * @reason Overwrite getNode as a stub
     */
    @Overwrite
    @VisibleForDebug
    public SectionOcclusionGraph.Node getNode(final SectionRenderDispatcher.RenderSection section) {
        return null;
    }
}
