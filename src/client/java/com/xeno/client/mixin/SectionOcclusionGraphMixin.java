package com.xeno.client.mixin;

import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingRequest;
import com.xeno.client.culling.CullingThread;
import it.unimi.dsi.fastutil.longs.LongCollection;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    @Unique
    private final CullingRequest[] xenoRequests = new CullingRequest[] { new CullingRequest(), new CullingRequest() };

    @Unique
    private int xenoWriteIndex = 0;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.xenoCullingThread = new CullingThread();
        this.xenoCullingThread.start();
    }

    /**
     * @author ExodusCoder9
     * @reason Delegates view area resets to the asynchronous culling thread.
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
     * @reason Frustum culling runs on the render thread to prevent chunk pop-in; the background thread only provides occlusion-visible sections.
     */
    @Overwrite
    public void addSectionsInFrustum(
            final Frustum frustum,
            final List<SectionRenderDispatcher.RenderSection> visibleSections,
            final List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections
    ) {
        if (this.xenoCullingThread == null) return;
        CullingOutput output = this.xenoCullingThread.getLatestOutput();
        if (output == null) return;

        List<SectionRenderDispatcher.RenderSection> occlusionVisible = output.occlusionVisible();
        Vec3 camPos = output.cameraPos();
        BlockPos cameraCenter = SectionPos.of(camPos).center();

        for (SectionRenderDispatcher.RenderSection section : occlusionVisible) {
            AABB bb = section.getBoundingBox();

            if (frustum.isVisible(bb)) {
                visibleSections.add(section);

                if (cameraCenter.getX() > bb.minX - 32
                        && cameraCenter.getX() < bb.maxX + 32
                        && cameraCenter.getY() > bb.minY - 32
                        && cameraCenter.getY() < bb.maxY + 32
                        && cameraCenter.getZ() > bb.minZ - 32
                        && cameraCenter.getZ() < bb.maxZ + 32) {
                    nearbyVisibleSections.add(section);
                }
            }
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
     * @reason Submits a double-buffered snapshot of the render state to the background culling thread.
     */
    @Overwrite
    public void update(final CameraRenderState camera, final int fov, final ChunkLoadingRenderState chunkLoadingRenderState) {
        if (this.xenoCullingThread == null || this.xenoViewArea == null) return;

        this.updateLoadedChunks(chunkLoadingRenderState.addedLoadedChunks, chunkLoadingRenderState.removedLoadedChunks);
        this.updateEmptySections(chunkLoadingRenderState.addedEmptySections, chunkLoadingRenderState.removedEmptySections);

        if (!camera.isFrustumCaptured) {
            if (this.xenoCullingThread.isProcessing()) {
                return;
            }

            CullingRequest request = this.xenoRequests[this.xenoWriteIndex];

            request.cameraBlockPos = camera.blockPos;
            request.cameraPos = camera.pos;
            request.smartCull = camera.smartCull;
            request.frustum = camera.cullFrustum;
            request.fov = fov;
            request.viewArea = this.xenoViewArea;

            RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage = ((ViewAreaAccessor) this.xenoViewArea).getSections();

            int minY = this.xenoViewArea.minSectionY();
            int maxY = this.xenoViewArea.maxSectionY();
            int viewDistance = this.xenoViewArea.getViewDistance();
            int sizeY = maxY - minY + 1;
            int sizeXZ = viewDistance * 2 + 1;
            int totalSections = sizeXZ * sizeY * sizeXZ;

            if (request.sectionArray.length < totalSections) {
                request.sectionArray = new SectionRenderDispatcher.RenderSection[totalSections];
            }

            for (SectionRenderDispatcher.RenderSection section : storage) {
                if (section != null) {
                    request.sectionArray[section.index] = section;
                }
            }

            request.minY = minY;
            request.maxY = maxY;
            request.sizeY = sizeY;
            request.sizeXZ = sizeXZ;
            request.viewDistance = viewDistance;

            request.emptySections.clear();
            request.emptySections.addAll(this.emptySections);

            request.loadedChunks.clear();
            request.loadedChunks.addAll(this.loadedChunks);

            request.propagations.clear();
            request.propagations.addAll(this.pendingPropagations);
            this.pendingPropagations.clear();

            this.xenoCullingThread.submitRequest(request);
            this.xenoWriteIndex = (this.xenoWriteIndex + 1) % 2;
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Intercepts empty section updates to schedule propagations and update the local tracker.
     */
    @Overwrite
    public void updateEmptySections(final LongOpenHashSet added, final LongOpenHashSet removed) {
        this.emptySections.addAll(added);
        var iter = removed.longIterator();
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
