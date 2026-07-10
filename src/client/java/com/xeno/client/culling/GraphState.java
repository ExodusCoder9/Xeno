package com.xeno.client.culling;

import net.minecraft.core.Direction;

public final class GraphState {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int MIN_ADVANCED_CULLING_SECTION_DISTANCE = 4;
    private static final double CEILED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);

    public final XenoNode[] nodes;
    public final int[] bfsQueue;
    public final long[] bfsQueueSectionNodes;
    public int bfsHead;
    public int bfsTail;
    public final int totalSections;

    public GraphState(int maxSections, int queueCapacity) {
        this.totalSections = maxSections;
        this.nodes = new XenoNode[maxSections];
        this.bfsQueue = new int[queueCapacity];
        this.bfsQueueSectionNodes = new long[queueCapacity];
        this.bfsHead = 0;
        this.bfsTail = 0;

        for (int i = 0; i < maxSections; i++) {
            nodes[i] = new XenoNode();
        }
    }

    public void reset() {
        bfsHead = 0;
        bfsTail = 0;
    }

    public boolean queueEmpty() {
        return bfsHead == bfsTail;
    }

    public int queueSize() {
        return (bfsTail - bfsHead + bfsQueue.length) % bfsQueue.length;
    }

    public void enqueue(int sectionIndex, long sectionNode, int step) {
        int nextTail = (bfsTail + 1) % bfsQueue.length;
        if (nextTail == bfsHead) {
            return;
        }
        XenoNode node = nodes[sectionIndex];
        node.reset(sectionNode, sectionIndex, step);
        bfsQueue[bfsTail] = sectionIndex;
        bfsQueueSectionNodes[bfsTail] = sectionNode;
        bfsTail = nextTail;
    }

    public XenoNode dequeue() {
        if (bfsHead == bfsTail) return null;
        int sectionIndex = bfsQueue[bfsHead];
        long sectionNode = bfsQueueSectionNodes[bfsHead];
        bfsHead = (bfsHead + 1) % bfsQueue.length;
        XenoNode node = nodes[sectionIndex];
        node.sectionNode = sectionNode;
        return node;
    }

    public static int getNeighborIndex(long sectionNode, Direction dir, CullingSnapshot snap) {
        int sx = net.minecraft.core.SectionPos.x(sectionNode);
        int sy = net.minecraft.core.SectionPos.y(sectionNode);
        int sz = net.minecraft.core.SectionPos.z(sectionNode);

        int nx = sx + dir.getStepX();
        int ny = sy + dir.getStepY();
        int nz = sz + dir.getStepZ();

        if (ny < snap.minSectionY || ny > snap.maxSectionY) return -1;

        int halfRadius = snap.sectionGridSizeXZ / 2;
        int relX = nx - (net.minecraft.core.SectionPos.x(snap.cameraSectionNode));
        int relZ = nz - (net.minecraft.core.SectionPos.z(snap.cameraSectionNode));

        if (Math.abs(relX) > halfRadius || Math.abs(relZ) > halfRadius) return -1;

        int gridX = Math.floorMod(nx, snap.sectionGridSizeXZ);
        int gridY = ny - snap.minSectionY;
        int gridZ = Math.floorMod(nz, snap.sectionGridSizeXZ);

        return (gridZ * snap.sectionGridSizeY + gridY) * snap.sectionGridSizeXZ + gridX;
    }

    public static long getNeighborSectionNode(long sectionNode, Direction dir) {
        return net.minecraft.core.SectionPos.offset(sectionNode, dir);
    }

    public static boolean isDistantFromCamera(long sectionNode, CullingSnapshot snap) {
        int camSX = net.minecraft.core.SectionPos.x(snap.cameraSectionNode);
        int camSY = net.minecraft.core.SectionPos.y(snap.cameraSectionNode);
        int camSZ = net.minecraft.core.SectionPos.z(snap.cameraSectionNode);

        return Math.abs(net.minecraft.core.SectionPos.x(sectionNode) - camSX) > MIN_ADVANCED_CULLING_SECTION_DISTANCE
                || Math.abs(net.minecraft.core.SectionPos.y(sectionNode) - camSY) > MIN_ADVANCED_CULLING_SECTION_DISTANCE
                || Math.abs(net.minecraft.core.SectionPos.z(sectionNode) - camSZ) > MIN_ADVANCED_CULLING_SECTION_DISTANCE;
    }
}
