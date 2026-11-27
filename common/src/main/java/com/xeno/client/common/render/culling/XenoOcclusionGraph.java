/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.common.render.culling;

import com.xeno.client.mixin.ViewAreaAccessor;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class XenoOcclusionGraph extends SectionOcclusionGraph {
    private static final int CAMERA_CUBE_OFFSET = 8;
    private static final double ENTER_SYNC_STEP_THRESHOLD = 32.0;
    private static final double EXIT_SYNC_STEP_THRESHOLD = 20.0;
    private static final double LOCAL_POS_THRESHOLD = 4.0;
    private static final float LOCAL_ROT_THRESHOLD = 5.0f;

    private @Nullable ViewArea viewArea;
    private boolean needsWideFull = true;
    private boolean needsLocalFull = true;
    private boolean hasPendingPropagation;

    private int prevWideSecX = Integer.MIN_VALUE;
    private int prevWideSecY = Integer.MIN_VALUE;
    private int prevWideSecZ = Integer.MIN_VALUE;
    private int prevWideFov;
    private boolean lastSmartCull;
    private int prevCenterSecX = Integer.MIN_VALUE;
    private int prevCenterSecZ = Integer.MIN_VALUE;

    private double prevFrameCamX;
    private double prevFrameCamY;
    private double prevFrameCamZ;
    private boolean hasPrevFrameCam;
    private boolean syncFallbackActive;

    private double prevLocalCamX;
    private double prevLocalCamY;
    private double prevLocalCamZ;
    private float prevLocalXRot;
    private float prevLocalYRot;
    private int prevLocalFov;
    private long localCameraSeq;

    private int lastSubmitBlockX;
    private int lastSubmitBlockY;
    private int lastSubmitBlockZ;

    private @Nullable Frustum reusableOffsetFrustum;
    private final XenoBranchlessFrustum[] frustumPool = {
            new XenoBranchlessFrustum(),
            new XenoBranchlessFrustum(),
            new XenoBranchlessFrustum(),
            new XenoBranchlessFrustum()
    };
    private int frustumPoolCursor;

    private @Nullable Octree fallbackOctree;

    @Override
    public void waitAndReset(@Nullable ViewArea viewArea) {
        this.viewArea = viewArea;
        this.needsWideFull = true;
        this.needsLocalFull = true;
        this.hasPendingPropagation = false;
        this.prevWideSecX = Integer.MIN_VALUE;
        this.prevWideSecY = Integer.MIN_VALUE;
        this.prevWideSecZ = Integer.MIN_VALUE;
        this.prevCenterSecX = Integer.MIN_VALUE;
        this.prevCenterSecZ = Integer.MIN_VALUE;
        this.hasPrevFrameCam = false;
        this.syncFallbackActive = false;
        this.localCameraSeq++;

        if (viewArea != null) {
            XenoOcclusionCuller.INSTANCE.reset(viewArea, ((ViewAreaAccessor) viewArea).xeno$getSections());
        } else {
            XenoOcclusionCuller.INSTANCE.reset(null, null);
        }
    }

    @Override
    public @NonNull LongCollection expectedChunks() {
        return XenoOcclusionCuller.INSTANCE.expectedChunks();
    }

    @Override
    public void invalidate() {
        this.needsWideFull = true;
        this.needsLocalFull = true;
    }

    @Override
    public void invalidateIfNeeded(CameraRenderState cameraState, int fov) {
        int secX = SectionPos.blockToSectionCoord(cameraState.blockPos.getX());
        int secY = SectionPos.blockToSectionCoord(cameraState.blockPos.getY());
        int secZ = SectionPos.blockToSectionCoord(cameraState.blockPos.getZ());
        if (secX != this.prevWideSecX
                || secY != this.prevWideSecY
                || secZ != this.prevWideSecZ
                || fov != this.prevWideFov
                || cameraState.smartCull != this.lastSmartCull) {
            this.prevWideSecX = secX;
            this.prevWideSecY = secY;
            this.prevWideSecZ = secZ;
            this.prevWideFov = fov;
            this.lastSmartCull = cameraState.smartCull;
            this.needsWideFull = true;
            this.needsLocalFull = true;
        }
    }

    @Override
    public void schedulePropagationFrom(SectionRenderDispatcher.@NonNull RenderSection section) {
        this.hasPendingPropagation = true;
        XenoOcclusionCuller.INSTANCE.enqueuePropagation(section);
    }

    @Override
    public boolean consumeFrustumUpdate() {
        return XenoOcclusionCuller.INSTANCE.consumeFrustumUpdate();
    }

    @Override
    public void updateEmptySections(@NonNull LongOpenHashSet added, @NonNull LongOpenHashSet removed) {
    }

    @Override
    public void updateLoadedChunks(@NonNull LongOpenHashSet added, @NonNull LongOpenHashSet removed) {
    }

    @Override
    public void update(CameraRenderState cameraState, int fov, ChunkLoadingRenderState chunkLoadingState) {
        XenoOcclusionCuller.INSTANCE.enqueueChunkDelta(
                chunkLoadingState.addedLoadedChunks,
                chunkLoadingState.removedLoadedChunks,
                chunkLoadingState.addedEmptySections,
                chunkLoadingState.removedEmptySections
        );

        boolean hasRetractions = !chunkLoadingState.removedLoadedChunks.isEmpty()
                || !chunkLoadingState.addedEmptySections.isEmpty();
        boolean hasChunkChanges = hasRetractions
                || !chunkLoadingState.addedLoadedChunks.isEmpty()
                || !chunkLoadingState.removedEmptySections.isEmpty();

        if (cameraState.isFrustumCaptured || this.viewArea == null) {
            return;
        }

        double camX = cameraState.pos.x;
        double camY = cameraState.pos.y;
        double camZ = cameraState.pos.z;
        this.updateSyncFallbackState(camX, camY, camZ);

        this.lastSubmitBlockX = cameraState.blockPos.getX();
        this.lastSubmitBlockY = cameraState.blockPos.getY();
        this.lastSubmitBlockZ = cameraState.blockPos.getZ();

        this.invalidateIfNeeded(cameraState, fov);

        boolean localAngleChanged = Math.abs(cameraState.xRot - this.prevLocalXRot) >= LOCAL_ROT_THRESHOLD
                || Math.abs(cameraState.yRot - this.prevLocalYRot) >= LOCAL_ROT_THRESHOLD
                || fov != this.prevLocalFov;
        boolean localPosChanged = Math.abs(camX - this.prevLocalCamX) >= LOCAL_POS_THRESHOLD
                || Math.abs(camY - this.prevLocalCamY) >= LOCAL_POS_THRESHOLD
                || Math.abs(camZ - this.prevLocalCamZ) >= LOCAL_POS_THRESHOLD;
        if (localAngleChanged || localPosChanged) {
            this.prevLocalCamX = camX;
            this.prevLocalCamY = camY;
            this.prevLocalCamZ = camZ;
            this.prevLocalXRot = cameraState.xRot;
            this.prevLocalYRot = cameraState.yRot;
            this.prevLocalFov = fov;
            this.needsLocalFull = true;
            if (localAngleChanged) {
                this.localCameraSeq++;
                XenoOcclusionCuller.INSTANCE.invalidateLocalSnapshot(this.localCameraSeq);
            }
        }

        SectionPos centerPos = this.viewArea.getCameraSectionPos();
        if (centerPos.x() != this.prevCenterSecX || centerPos.z() != this.prevCenterSecZ) {
            this.prevCenterSecX = centerPos.x();
            this.prevCenterSecZ = centerPos.z();
            this.needsWideFull = true;
            this.needsLocalFull = true;
        }

        if (hasRetractions) {
            this.needsWideFull = true;
            this.needsLocalFull = true;
        }

        boolean hasLoadedExpected = !chunkLoadingState.loadedExpectedChunks.isEmpty();
        if (hasLoadedExpected) {
            XenoOcclusionCuller.INSTANCE.enqueueLoadedExpectedChunks(chunkLoadingState.loadedExpectedChunks);
        }

        boolean forceWide = this.needsWideFull;
        boolean forceLocal = this.needsLocalFull;
        boolean dirtyPropagation = this.hasPendingPropagation;
        if (forceWide || forceLocal || hasChunkChanges || dirtyPropagation || hasLoadedExpected) {
            this.needsWideFull = false;
            this.needsLocalFull = false;
            this.hasPendingPropagation = false;

            XenoBranchlessFrustum frustumSnapshot = null;
            if (cameraState.cullFrustum instanceof XenoFrustumProvider provider) {
                frustumSnapshot = this.frustumPool[this.frustumPoolCursor];
                this.frustumPoolCursor = (this.frustumPoolCursor + 1) & 3;
                frustumSnapshot.copyFrom(provider.xeno$getBranchlessFrustum());
            }

            XenoOcclusionCuller.INSTANCE.submitRequest(
                    forceWide,
                    forceLocal,
                    forceWide,
                    this.localCameraSeq,
                    camX,
                    camY,
                    camZ,
                    this.lastSubmitBlockX,
                    this.lastSubmitBlockY,
                    this.lastSubmitBlockZ,
                    cameraState.smartCull,
                    frustumSnapshot,
                    centerPos
            );
        }
    }

    private void updateSyncFallbackState(double camX, double camY, double camZ) {
        if (!this.hasPrevFrameCam) {
            this.prevFrameCamX = camX;
            this.prevFrameCamY = camY;
            this.prevFrameCamZ = camZ;
            this.hasPrevFrameCam = true;
            this.syncFallbackActive = false;
            return;
        }
        double dist = Math.max(
                Math.abs(camX - this.prevFrameCamX),
                Math.max(Math.abs(camY - this.prevFrameCamY), Math.abs(camZ - this.prevFrameCamZ))
        );
        if (this.syncFallbackActive && dist <= EXIT_SYNC_STEP_THRESHOLD) {
            this.syncFallbackActive = false;
        } else if (!this.syncFallbackActive && dist >= ENTER_SYNC_STEP_THRESHOLD) {
            this.syncFallbackActive = true;
        }
        this.prevFrameCamX = camX;
        this.prevFrameCamY = camY;
        this.prevFrameCamZ = camZ;
    }

    @Override
    public void addSectionsInFrustum(
            @NonNull Frustum frustum,
            @NonNull List<SectionRenderDispatcher.RenderSection> outVisible,
            @NonNull List<SectionRenderDispatcher.RenderSection> outNearby
    ) {
        if (this.reusableOffsetFrustum == null) {
            this.reusableOffsetFrustum = new Frustum(frustum);
        } else {
            this.reusableOffsetFrustum.set(frustum);
        }
        Frustum offset = this.reusableOffsetFrustum.offsetToFullyIncludeCameraCube(CAMERA_CUBE_OFFSET);
        int camSecX = SectionPos.blockToSectionCoord(this.lastSubmitBlockX);
        int camSecY = SectionPos.blockToSectionCoord(this.lastSubmitBlockY);
        int camSecZ = SectionPos.blockToSectionCoord(this.lastSubmitBlockZ);
        XenoOcclusionCuller.INSTANCE.collectVisibleSections(
                offset,
                this.syncFallbackActive,
                camSecX,
                camSecY,
                camSecZ,
                this.lastSubmitBlockX,
                this.lastSubmitBlockY,
                this.lastSubmitBlockZ,
                outVisible,
                outNearby
        );
    }

    @Override
    public SectionOcclusionGraph.@Nullable Node getNode(SectionRenderDispatcher.@NonNull RenderSection section) {
        return null;
    }

    @Override
    public @NonNull Octree getOctree() {
        if (this.fallbackOctree == null) {
            this.fallbackOctree = new Octree(SectionPos.of(0, 0, 0), 0, 1, 0);
        }
        return this.fallbackOctree;
    }
}
