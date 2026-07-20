package com.xeno.client.renderer.culling;

import com.xeno.client.XenoClient;
import com.xeno.client.mixin.ViewAreaAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Culling Manager that manages the culling threads, requests, culling graphs,
 * and handles main-thread frustum culling checks to eliminate chunk pop-in.
 */
public class XenoCullingManager implements IXenoCullingManager {

    private final XenoCullingGraph cullingGraph = new XenoCullingGraph();
    private final CullingThread cullingThread;
    private ViewArea viewArea;
    private final List<SectionRenderDispatcher.RenderSection> pendingPropagations = new ArrayList<>();
    private final CullingRequest[] requests = new CullingRequest[] { new CullingRequest(), new CullingRequest() };
    private int writeIndex = 0;
    private boolean queuedUpdateAfterReset;

    public XenoCullingManager() {
        this.cullingThread = new CullingThread();
        this.cullingThread.start();
        XenoClient.setCullingThread(this.cullingThread);
    }

    public XenoCullingGraph getCullingGraph() {
        return this.cullingGraph;
    }

    @Override
    public void waitAndReset(ViewArea viewArea) {
        this.viewArea = viewArea;
        XenoClient.setViewArea(viewArea);
        this.pendingPropagations.clear();
        this.cullingThread.reset();
        this.cullingGraph.clear();
        XenoVisibility.invalidate();
        this.queuedUpdateAfterReset = true;
    }

    @Override
    public void invalidate() {
        this.cullingThread.invalidate();
    }

    @Override
    public void schedulePropagationFrom(SectionRenderDispatcher.RenderSection section) {
        this.pendingPropagations.add(section);
    }

    @Override
    public void update(
            CameraRenderState camera,
            int fov,
            ChunkLoadingRenderState chunkLoading,
            LongOpenHashSet emptySections,
            LongOpenHashSet loadedChunks
    ) {
        if (this.viewArea == null) return;

        XenoClient.setCameraState(camera.yRot, camera.xRot, camera.pos);

        this.updateLoadedChunks(loadedChunks, chunkLoading.addedLoadedChunks, chunkLoading.removedLoadedChunks);
        this.updateEmptySections(emptySections, chunkLoading.addedEmptySections, chunkLoading.removedEmptySections);

        if (this.cullingThread.isProcessing()) {
            return;
        }

        if (!camera.isFrustumCaptured) {
            CullingRequest request = this.requests[this.writeIndex];

            request.cameraBlockPos = camera.blockPos;
            request.cameraPos = camera.pos;
            request.smartCull = camera.smartCull;
            request.frustum = camera.cullFrustum;
            request.fov = fov;
            request.cameraYaw = camera.yRot;
            request.cameraPitch = camera.xRot;
            request.viewArea = this.viewArea;

            RotatingSectionStorage<SectionRenderDispatcher.RenderSection> storage =
                    ((ViewAreaAccessor) this.viewArea).getSections();

            int minY = this.viewArea.minSectionY();
            int maxY = this.viewArea.maxSectionY();
            int viewDistance = this.viewArea.getViewDistance();
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
            request.emptySections.addAll(emptySections);

            request.loadedChunks.clear();
            request.loadedChunks.addAll(loadedChunks);

            request.propagations.clear();
            request.propagations.addAll(this.pendingPropagations);
            this.pendingPropagations.clear();

            request.cancelled = false;
            this.cullingThread.submitRequest(request);
            this.writeIndex = (this.writeIndex + 1) % 2;
            this.queuedUpdateAfterReset = false;
        }
    }

    private void updateEmptySections(LongOpenHashSet emptySections, LongOpenHashSet added, LongOpenHashSet removed) {
        emptySections.addAll(added);
        var iter = removed.longIterator();
        while (iter.hasNext()) {
            long sectionNode = iter.nextLong();
            if (emptySections.remove(sectionNode)) {
                SectionRenderDispatcher.RenderSection section =
                        ((ViewAreaAccessor) this.viewArea).invokeGetRenderSection(sectionNode);
                if (section != null) {
                    this.pendingPropagations.add(section);
                    section.setWasPreviouslyEmpty(true);
                }
            }
        }
    }

    private void updateLoadedChunks(LongOpenHashSet loadedChunks, LongOpenHashSet added, LongOpenHashSet removed) {
        loadedChunks.addAll(added);
        loadedChunks.removeAll(removed);
    }

    @Override
    public void addSectionsInFrustum(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> visibleSections,
            List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections
    ) {
        CullingOutput output = this.cullingThread.getLatestOutput();
        if (output == null) return;

        XenoVisibility.publish(output.sectionVisibility(), output.opaqueSections());

        // Update our custom culling graph with latest visibility and opacity info
        this.cullingGraph.clear();
        for (SectionRenderDispatcher.RenderSection section : output.occlusionVisible()) {
            XenoCullingGraph.Node node = this.cullingGraph.getOrCreateNode(section);
            node.setVisible(true);
            node.setOpaque(output.opaqueSections().get(section.getSectionNode()));
        }

        List<SectionRenderDispatcher.RenderSection> occlusionVisible = output.occlusionVisible();
        Vec3 camPos = output.cameraPos();
        BlockPos cameraCenter = SectionPos.of(camPos).center();

        for (SectionRenderDispatcher.RenderSection section : occlusionVisible) {
            AABB bb = section.getBoundingBox();

            // Perform render-thread frustum check on the bounding box to eliminate camera rotation pop-in
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

    @Override
    public boolean consumeFrustumUpdate() {
        return this.cullingThread.consumeFrustumUpdate();
    }

    @Override
    public Octree getOctree() {
        return this.cullingThread.getOctree();
    }

    @Override
    public void destroy() {
        this.cullingThread.reset();
        this.cullingGraph.clear();
    }
}
