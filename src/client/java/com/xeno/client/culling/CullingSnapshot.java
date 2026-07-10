package com.xeno.client.culling;

import net.minecraft.client.renderer.culling.Frustum;

public final class CullingSnapshot {
    public final double camX;
    public final double camY;
    public final double camZ;
    public final long cameraSectionNode;
    public final Frustum frustum;
    public final int fov;
    public final boolean smartCull;

    public final long[] sectionNodes;
    public final boolean[] hasMesh;
    public final int sectionCount;
    public final int viewDistance;
    public final int minSectionY;
    public final int maxSectionY;
    public final int sectionGridSizeXZ;
    public final int sectionGridSizeY;

    public final long prevCameraSectionNode;

    public final byte[] visibilityLookup;
    private static final int DIR_COUNT = 6;
    private static final int LOOKUP_SIZE = DIR_COUNT * DIR_COUNT;

    public CullingSnapshot(
            double camX, double camY, double camZ,
            long cameraSectionNode,
            Frustum frustum,
            int fov,
            boolean smartCull,
            long[] sectionNodes,
            boolean[] hasMesh,
            byte[] visibilityLookup,
            int sectionCount,
            int viewDistance,
            int minSectionY,
            int maxSectionY,
            int sectionGridSizeXZ,
            int sectionGridSizeY,
            long prevCameraSectionNode
    ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.cameraSectionNode = cameraSectionNode;
        this.frustum = frustum;
        this.fov = fov;
        this.smartCull = smartCull;
        this.sectionNodes = sectionNodes;
        this.hasMesh = hasMesh;
        this.visibilityLookup = visibilityLookup;
        this.sectionCount = sectionCount;
        this.viewDistance = viewDistance;
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
        this.sectionGridSizeXZ = sectionGridSizeXZ;
        this.sectionGridSizeY = sectionGridSizeY;
        this.prevCameraSectionNode = prevCameraSectionNode;
    }

    public boolean facesCanSeeEachother(int sectionIndex, int fromDirOrdinal, int toDirOrdinal) {
        if (sectionIndex < 0 || sectionIndex >= sectionCount) return false;
        return visibilityLookup[sectionIndex * LOOKUP_SIZE + fromDirOrdinal * DIR_COUNT + toDirOrdinal] != 0;
    }
}
