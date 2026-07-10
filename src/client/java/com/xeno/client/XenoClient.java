package com.xeno.client;

import com.mojang.logging.LogUtils;
import com.xeno.client.culling.CullingSnapshot;
import com.xeno.client.culling.CullingThread;
import com.xeno.client.mixin.ViewAreaAccessor;
import com.xeno.client.renderer.XenoOctree;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class XenoClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SECTIONS = 65536;

    private static @Nullable CullingThread cullingThread;
    private static @Nullable XenoOctree currentOctree;
    private static @Nullable LevelRenderer currentLevelRenderer;
    private static volatile boolean active;
    private static long lastCameraSectionNode = Long.MIN_VALUE;
    private static boolean hasLoggedFirstSnapshot;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Xeno] Initializing occlusion culling system");
        cullingThread = new CullingThread(MAX_SECTIONS);
        cullingThread.start();
        active = true;
        LOGGER.info("[Xeno] Culling thread started");
    }

    public static void onRenderFrame(CameraRenderState cameraState) {
        if (!active || cullingThread == null) return;
        if (currentLevelRenderer == null) return;

        ViewArea viewArea = currentLevelRenderer.viewArea();
        if (viewArea == null) return;

        long cameraSectionNode = SectionPos.asLong(cameraState.blockPos);
        boolean cameraMoved = cameraSectionNode != lastCameraSectionNode;

        if (cameraMoved) {
            rebuildOctree(viewArea, cameraSectionNode);
            lastCameraSectionNode = cameraSectionNode;
        }

        if (currentOctree == null) {
            rebuildOctree(viewArea, cameraSectionNode);
            if (currentOctree == null) return;
        }

        CullingSnapshot snapshot = buildSnapshot(viewArea, cameraState, cameraSectionNode);
        if (snapshot != null) {
            if (!hasLoggedFirstSnapshot) {
                LOGGER.info("[Xeno] First snapshot submitted: {} sections, camera at ({}, {}, {})",
                        snapshot.sectionCount(),
                        SectionPos.x(cameraSectionNode), SectionPos.y(cameraSectionNode), SectionPos.z(cameraSectionNode));
                hasLoggedFirstSnapshot = true;
            }
            cullingThread.submitSnapshot(snapshot);
        }
    }

    private static void rebuildOctree(ViewArea viewArea, long cameraSectionNode) {
        try {
            int renderDistance = viewArea.getViewDistance();
            int minSectionY = viewArea.minSectionY();
            currentOctree = new XenoOctree(cameraSectionNode, renderDistance, minSectionY);

            RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections =
                    ((ViewAreaAccessor) viewArea).xeno$getSections();

            int added = 0;
            for (SectionRenderDispatcher.RenderSection section : sections) {
                if (section != null) {
                    currentOctree.add(section);
                    added++;
                }
            }
            LOGGER.info("[Xeno] Octree rebuilt: {} sections, renderDistance={}, minSectionY={}", added, renderDistance, minSectionY);
        } catch (Exception e) {
            LOGGER.error("[Xeno] Failed to rebuild octree", e);
            currentOctree = null;
        }
    }

    private static @Nullable CullingSnapshot buildSnapshot(
            ViewArea viewArea,
            CameraRenderState cameraState,
            long cameraSectionNode
    ) {
        try {
            RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections =
                    ((ViewAreaAccessor) viewArea).xeno$getSections();

            int sectionCount = 0;
            int meshedCount = 0;
            int gridSizeXZ = sections.radius() * 2 + 1;
            int gridSizeY = sections.height();
            int minSectionY = viewArea.minSectionY();
            int maxSectionY = viewArea.maxSectionY();

            long[] sectionNodes = new long[MAX_SECTIONS];
            boolean[] hasMesh = new boolean[MAX_SECTIONS];
            byte[] visibilityLookup = new byte[MAX_SECTIONS * 36];

            for (SectionRenderDispatcher.RenderSection section : sections) {
                if (section == null) continue;
                int idx = section.index;
                if (idx < 0 || idx >= MAX_SECTIONS) continue;

                sectionNodes[idx] = section.getSectionNode();
                SectionMesh mesh = section.getSectionMesh();
                boolean meshValid = mesh != CompiledSectionMesh.UNCOMPILED;

                hasMesh[idx] = meshValid;
                if (meshValid) meshedCount++;

                if (meshValid) {
                    for (int from = 0; from < 6; from++) {
                        for (int to = 0; to < 6; to++) {
                            int lookupIdx = idx * 36 + from * 6 + to;
                            visibilityLookup[lookupIdx] = mesh.facesCanSeeEachother(
                                    net.minecraft.core.Direction.values()[from],
                                    net.minecraft.core.Direction.values()[to]
                            ) ? (byte) 1 : (byte) 0;
                        }
                    }
                }

                sectionCount = Math.max(sectionCount, idx + 1);
            }

            if (!hasLoggedFirstSnapshot) {
                LOGGER.info("[Xeno] Snapshot build: {} total sections, {} with meshes, grid={}x{}", sectionCount, meshedCount, gridSizeXZ, gridSizeY);
            }

            return new CullingSnapshot(
                    cameraState.pos.x, cameraState.pos.y, cameraState.pos.z,
                    cameraSectionNode,
                    cameraState.cullFrustum,
                    (int) cameraState.hudFov,
                    cameraState.smartCull,
                    sectionNodes,
                    hasMesh,
                    visibilityLookup,
                    sectionCount,
                    viewArea.getViewDistance(),
                    minSectionY,
                    maxSectionY,
                    gridSizeXZ,
                    gridSizeY,
                    lastCameraSectionNode
            );
        } catch (Exception e) {
            LOGGER.error("[Xeno] Failed to build snapshot", e);
            return null;
        }
    }

    public static void setLevelRenderer(@Nullable LevelRenderer renderer) {
        currentLevelRenderer = renderer;
        if (renderer == null) {
            currentOctree = null;
            lastCameraSectionNode = Long.MIN_VALUE;
            if (cullingThread != null) {
                cullingThread.invalidate();
            }
        }
    }

    public static boolean isActive() {
        return active && cullingThread != null;
    }

    public static @Nullable CullingThread getCullingThread() {
        return cullingThread;
    }

    public static @Nullable XenoOctree getOctree() {
        return currentOctree;
    }
}
