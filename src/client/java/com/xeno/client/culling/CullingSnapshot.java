package com.xeno.client.culling;

import net.minecraft.client.renderer.culling.Frustum;

public record CullingSnapshot(
        double camX,
        double camY,
        double camZ,
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
    private static final int DIR_COUNT = 6;
    private static final int LOOKUP_SIZE = DIR_COUNT * DIR_COUNT;

    public boolean facesCanSeeEachother(int sectionIndex, int fromDirOrdinal, int toDirOrdinal) {
        if (sectionIndex < 0 || sectionIndex >= sectionCount) return false;
        return visibilityLookup[sectionIndex * LOOKUP_SIZE + fromDirOrdinal * DIR_COUNT + toDirOrdinal] != 0;
    }
}
