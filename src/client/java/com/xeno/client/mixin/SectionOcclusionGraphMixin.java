package com.xeno.client.mixin;

import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingRequest;
import com.xeno.client.culling.CullingThread;
import com.xeno.client.culling.XenoOcclusionGraph;
import com.xeno.client.mixin.FrustumAccessor;
import org.joml.FrustumIntersection;
import net.minecraft.world.phys.AABB;
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
public class SectionOcclusionGraphMixin implements XenoOcclusionGraph {
    @Shadow @Final
    private LongOpenHashSet emptySections;

    @Shadow @Final
    private LongOpenHashSet loadedChunks;

    @Unique
    private CullingThread xeno_cullingThread;

    @Unique
    private ViewArea xeno_viewArea;

    @Unique
    private final List<SectionRenderDispatcher.RenderSection> xeno_pendingPropagations = new ArrayList<>();

    @Unique
    private final CullingRequest[] xeno_requests = new CullingRequest[] { new CullingRequest(), new CullingRequest() };

    @Unique
    private int xeno_writeIndex = 0;

    @Unique
    private boolean xeno_needsFullUpdate = true;
    @Unique
    private double xeno_prevCamX = Double.MIN_VALUE;
    @Unique
    private double xeno_prevCamY = Double.MIN_VALUE;
    @Unique
    private double xeno_prevCamZ = Double.MIN_VALUE;
    @Unique
    private int xeno_prevFov = Integer.MAX_VALUE;
    @Unique
    private boolean xeno_lastSmartCull = true;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xeno_onInit(CallbackInfo ci) {
        this.xeno_cullingThread = new CullingThread();
        this.xeno_cullingThread.start();
    }

    /**
     * @author ExodusCoder9
     * @reason Delegates view area resets to the asynchronous Xeno culling thread.
     */
    @Overwrite
    public void waitAndReset(final @Nullable ViewArea viewArea) {
        this.xeno_viewArea = viewArea;
        this.xeno_pendingPropagations.clear();
        this.xeno_needsFullUpdate = true;
        this.xeno_prevCamX = Double.MIN_VALUE;
        this.xeno_prevCamY = Double.MIN_VALUE;
        this.xeno_prevCamZ = Double.MIN_VALUE;
        this.xeno_prevFov = Integer.MAX_VALUE;
        if (this.xeno_cullingThread != null) {
            this.xeno_cullingThread.reset();
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
        this.xeno_needsFullUpdate = true;
        if (this.xeno_cullingThread != null) {
            this.xeno_cullingThread.invalidate();
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Tracks camera movements across 8-block boundaries.
     */
    @Overwrite
    public void invalidateIfNeeded(final CameraRenderState camera, final int fov) {
        net.minecraft.world.phys.Vec3 cameraPos = camera.pos;
        double camX = Math.floor(cameraPos.x / 8.0);
        double camY = Math.floor(cameraPos.y / 8.0);
        double camZ = Math.floor(cameraPos.z / 8.0);
        if (camX != this.xeno_prevCamX || camY != this.xeno_prevCamY || camZ != this.xeno_prevCamZ || this.xeno_prevFov != fov || this.xeno_lastSmartCull != camera.smartCull) {
            this.xeno_needsFullUpdate = true;
            this.xeno_prevCamX = camX;
            this.xeno_prevCamY = camY;
            this.xeno_prevCamZ = camZ;
            this.xeno_prevFov = fov;
            this.xeno_lastSmartCull = camera.smartCull;
        }
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
        if (this.xeno_cullingThread == null) return;
        CullingOutput output = this.xeno_cullingThread.getLatestOutput();
        if (output != null) {
            double camX = frustum.getCamX();
            double camY = frustum.getCamY();
            double camZ = frustum.getCamZ();
            FrustumIntersection intersection = ((FrustumAccessor) frustum).xeno$getIntersection();

            for (SectionRenderDispatcher.RenderSection section : output.visibleSections()) {
                AABB bb = section.getBoundingBox();
                float minX = (float) (bb.minX - camX);
                float minY = (float) (bb.minY - camY);
                float minZ = (float) (bb.minZ - camZ);
                float maxX = (float) (bb.maxX - camX);
                float maxY = (float) (bb.maxY - camY);
                float maxZ = (float) (bb.maxZ - camZ);

                int result = intersection.intersectAab(minX, minY, minZ, maxX, maxY, maxZ);
                if (result == -2 || result == -1) {
                    visibleSections.add(section);
                }
            }
            nearbyVisibleSections.addAll(output.nearbyVisibleSections());
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Checks the asynchronous culling thread for new frustum update results.
     */
    @Overwrite
    public boolean consumeFrustumUpdate() {
        if (this.xeno_cullingThread == null) return false;
        return this.xeno_cullingThread.consumeFrustumUpdate();
    }

    /**
     * @author ExodusCoder9
     * @reason Queues propagation tasks locally to be batch-submitted to the culling thread.
     */
    @Overwrite
    public void schedulePropagationFrom(final SectionRenderDispatcher.RenderSection section) {
        this.xeno_pendingPropagations.add(section);
    }

    /**
     * @author ExodusCoder9
     * @reason Submits a complete double-buffered snapshot of the render state to the background culling thread.
     */
    @Overwrite
    public void update(final CameraRenderState camera, final int fov, final ChunkLoadingRenderState chunkLoadingRenderState) {
        if (this.xeno_cullingThread == null || this.xeno_viewArea == null) return;

        this.updateLoadedChunks(chunkLoadingRenderState.addedLoadedChunks, chunkLoadingRenderState.removedLoadedChunks);
        this.updateEmptySections(chunkLoadingRenderState.addedEmptySections, chunkLoadingRenderState.removedEmptySections);

        this.invalidateIfNeeded(camera, fov);

        if (!this.xeno_pendingPropagations.isEmpty()) {
            this.xeno_needsFullUpdate = true;
        }

        if (!camera.isFrustumCaptured) {
            if (this.xeno_cullingThread.isProcessing()) {
                return; // Drop frame update to avoid memory overwrites while the thread is parsing
            }

            CullingRequest request = this.xeno_requests[this.xeno_writeIndex];

            request.cameraBlockPos = camera.blockPos;
            request.cameraPos = camera.pos;
            request.smartCull = camera.smartCull;
            request.frustum = camera.cullFrustum;
            request.fov = fov;
            request.viewArea = this.xeno_viewArea;
            request.needsFullBfs = this.xeno_needsFullUpdate;
            this.xeno_needsFullUpdate = false; // Reset the flag after submitting

            RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage = ((ViewAreaAccessor) this.xeno_viewArea).getSections();

            int minY = this.xeno_viewArea.minSectionY();
            int maxY = this.xeno_viewArea.maxSectionY();
            int viewDistance = this.xeno_viewArea.getViewDistance();
            int sizeY = maxY - minY + 1;
            int sizeXZ = viewDistance * 2 + 1;
            int totalSections = sizeXZ * sizeY * sizeXZ;

            if (request.sectionArray.length < totalSections) {
                request.sectionArray = new SectionRenderDispatcher.RenderSection[totalSections];
            } else {
                java.util.Arrays.fill(request.sectionArray, 0, totalSections, null);
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
            request.propagations.addAll(this.xeno_pendingPropagations);
            this.xeno_pendingPropagations.clear();

            this.xeno_cullingThread.submitRequest(request);
            this.xeno_writeIndex = (this.xeno_writeIndex + 1) % 2;
        }
    }

    /**
     * @author ExodusCoder9
     * @reason Intercepts empty section updates to schedule propagations and update the local tracker.
     */
    @Overwrite
    public void updateEmptySections(final LongOpenHashSet added, final LongOpenHashSet removed) {
        if (!added.isEmpty() || !removed.isEmpty()) {
            this.xeno_needsFullUpdate = true;
        }
        this.emptySections.addAll(added);
        LongIterator iter = removed.longIterator();
        while (iter.hasNext()) {
            long sectionNode = iter.nextLong();
            if (this.emptySections.remove(sectionNode)) {
                SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) this.xeno_viewArea).invokeGetRenderSection(sectionNode);
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
        if (!added.isEmpty() || !removed.isEmpty()) {
            this.xeno_needsFullUpdate = true;
        }
        this.loadedChunks.addAll(added);
        this.loadedChunks.removeAll(removed);
    }

    /**
     * @author ExodusCoder9
     * @reason Fetches the dummy octree from the culling thread for debug rendering compatibility.
     */
    @Overwrite
    public @Nullable Octree getOctree() {
        if (this.xeno_cullingThread == null) return null;
        return this.xeno_cullingThread.getOctree();
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

    @Override
    public boolean xeno$isSectionVisible(int sectionIndex) {
        if (this.xeno_cullingThread == null) return true;
        CullingOutput output = this.xeno_cullingThread.getLatestOutput();
        if (output == null) return true;
        return output.isSectionVisible(sectionIndex);
    }
}