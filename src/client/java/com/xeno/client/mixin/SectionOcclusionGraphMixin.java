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

    @Unique
    private boolean xenoQueuedUpdateAfterReset;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.xenoCullingThread = new CullingThread();
        this.xenoCullingThread.start();
        com.xeno.client.XenoClient.setCullingThread(this.xenoCullingThread);
    }

    @Overwrite
    public void waitAndReset(final @Nullable ViewArea viewArea) {
        this.xenoViewArea = viewArea;
        com.xeno.client.XenoClient.setViewArea(viewArea);
        this.pendingPropagations.clear();
        if (this.xenoCullingThread != null) {
            this.xenoCullingThread.reset();
        }
        com.xeno.client.culling.XenoVisibility.invalidate();
        this.xenoQueuedUpdateAfterReset = true;
    }

    @Overwrite
    public LongCollection expectedChunks() {
        return LongSets.EMPTY_SET;
    }

    @Overwrite
    public void invalidate() {
        if (this.xenoCullingThread != null) {
            this.xenoCullingThread.invalidate();
        }
    }

    @Overwrite
    public void invalidateIfNeeded(final CameraRenderState camera, final int fov) {
    }

    @Overwrite
    public void addSectionsInFrustum(
            final Frustum frustum,
            final List<SectionRenderDispatcher.RenderSection> visibleSections,
            final List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections
    ) {
        if (this.xenoCullingThread == null) return;
        CullingOutput output = this.xenoCullingThread.getLatestOutput();
        if (output == null) return;

        com.xeno.client.culling.XenoVisibility.publish(
                output.sectionVisibility(),
                output.opaqueSections()
        );

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

    @Overwrite
    public boolean consumeFrustumUpdate() {
        if (this.xenoCullingThread == null) return false;
        return this.xenoCullingThread.consumeFrustumUpdate();
    }

    @Overwrite
    public void schedulePropagationFrom(final SectionRenderDispatcher.RenderSection section) {
        this.pendingPropagations.add(section);
    }

    @Overwrite
    public void update(final CameraRenderState camera, final int fov, final ChunkLoadingRenderState chunkLoadingRenderState) {
        if (this.xenoCullingThread == null || this.xenoViewArea == null) return;

        com.xeno.client.XenoClient.setCameraState(camera.yRot, camera.xRot, camera.pos);

        this.updateLoadedChunks(chunkLoadingRenderState.addedLoadedChunks, chunkLoadingRenderState.removedLoadedChunks);
        this.updateEmptySections(chunkLoadingRenderState.addedEmptySections, chunkLoadingRenderState.removedEmptySections);

        if (!camera.isFrustumCaptured) {
            CullingRequest request = this.xenoRequests[this.xenoWriteIndex];

            request.cameraBlockPos = camera.blockPos;
            request.cameraPos = camera.pos;
            request.smartCull = camera.smartCull;
            request.frustum = camera.cullFrustum;
            request.fov = fov;
            request.cameraYaw = camera.yRot;
            request.cameraPitch = camera.xRot;
            request.viewArea = this.xenoViewArea;

            RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage = ((com.xeno.client.mixin.ViewAreaAccessor) this.xenoViewArea).getSections();

            int minY = this.xenoViewArea.minSectionY();
            int maxY = this.xenoViewArea.maxSectionY();
            int viewDistance = this.xenoViewArea.getViewDistance();
            int sizeY = maxY - minY + 1;
            int sizeXZ = viewDistance * 2 + 1;
            int totalSections = sizeXZ * sizeY * sizeXZ;

            if (request.sectionArray.length != totalSections) {
                request.sectionArray = new SectionRenderDispatcher.RenderSection[totalSections];
            } else {
                java.util.Arrays.fill(request.sectionArray, null);
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

            request.cancelled = false;
            this.xenoCullingThread.submitRequest(request);
            this.xenoWriteIndex = (this.xenoWriteIndex + 1) % 2;
            this.xenoQueuedUpdateAfterReset = false;
        }
    }

    @Overwrite
    public void updateEmptySections(final LongOpenHashSet added, final LongOpenHashSet removed) {
        this.emptySections.addAll(added);
        var iter = removed.longIterator();
        while (iter.hasNext()) {
            long sectionNode = iter.nextLong();
            if (this.emptySections.remove(sectionNode)) {
                SectionRenderDispatcher.RenderSection section = ((com.xeno.client.mixin.ViewAreaAccessor) this.xenoViewArea).invokeGetRenderSection(sectionNode);
                if (section != null) {
                    this.schedulePropagationFrom(section);
                    section.setWasPreviouslyEmpty(true);
                }
            }
        }
    }

    @Overwrite
    public void updateLoadedChunks(final LongOpenHashSet added, final LongOpenHashSet removed) {
        this.loadedChunks.addAll(added);
        this.loadedChunks.removeAll(removed);
    }

    @Overwrite
    public @Nullable Octree getOctree() {
        if (this.xenoCullingThread == null) return null;
        return this.xenoCullingThread.getOctree();
    }

    @Overwrite
    @VisibleForDebug
    public SectionOcclusionGraph.Node getNode(final SectionRenderDispatcher.RenderSection section) {
        return null;
    }
}