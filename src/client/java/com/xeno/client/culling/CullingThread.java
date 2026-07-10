package com.xeno.client.culling;

import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;

public final class CullingThread extends Thread {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final CullingResult result;
    private final GraphState graphState;
    private final Object snapshotLock = new Object();

    private volatile CullingSnapshot pendingSnapshot;
    private volatile boolean needsFullRebuild = true;
    private volatile boolean running = true;

    private long prevCameraSectionNode = Long.MIN_VALUE;

    public CullingThread(int maxSections) {
        super("XenoCullThread");
        setDaemon(true);
        setPriority(Thread.MIN_PRIORITY);
        this.result = new CullingResult(maxSections);
        this.graphState = new GraphState(maxSections, Math.min(maxSections * 2, 65536));
    }

    public CullingResult getResult() {
        return result;
    }

    public void submitSnapshot(CullingSnapshot snapshot) {
        synchronized (snapshotLock) {
            this.pendingSnapshot = snapshot;
            snapshotLock.notify();
        }
    }

    public void invalidate() {
        this.needsFullRebuild = true;
    }

    @Override
    public void run() {
        while (running) {
            CullingSnapshot snapshot;
            synchronized (snapshotLock) {
                snapshot = pendingSnapshot;
                if (snapshot == null) {
                    try {
                        snapshotLock.wait(100);
                    } catch (InterruptedException e) {
                        if (!running) break;
                    }
                    continue;
                }
                pendingSnapshot = null;
            }

            boolean cameraChanged = snapshot.cameraSectionNode != prevCameraSectionNode;
            if (needsFullRebuild || cameraChanged) {
                prevCameraSectionNode = snapshot.cameraSectionNode;
                needsFullRebuild = false;
            }

            runCullingPass(snapshot);
        }
    }

    private void runCullingPass(CullingSnapshot snap) {
        int totalSections = snap.sectionCount;
        result.init(totalSections);
        graphState.reset();

        initializeBFS(snap);
        runBFS(snap);
        finalizeOccluded(snap);

        result.publish();
    }

    private void initializeBFS(CullingSnapshot snap) {
        long camNode = snap.cameraSectionNode;
        int camSX = SectionPos.x(camNode);
        int camSY = SectionPos.y(camNode);
        int camSZ = SectionPos.z(camNode);

        int camIndex = getNodeIndex(camSX, camSY, camSZ, snap);
        if (camIndex >= 0 && camIndex < snap.sectionCount && snap.hasMesh[camIndex]) {
            graphState.enqueue(camIndex, camNode, 0);
            result.markSurelyVisible(camIndex);
        } else {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = camSX + dx;
                        int ny = camSY + dy;
                        int nz = camSZ + dz;
                        int idx = getNodeIndex(nx, ny, nz, snap);
                        if (idx >= 0 && idx < snap.sectionCount && snap.hasMesh[idx]) {
                            long node = SectionPos.asLong(nx, ny, nz);
                            graphState.enqueue(idx, node, 0);
                            result.markSurelyVisible(idx);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < snap.sectionCount; i++) {
            if (result.getWriteVisibility(i) == CullingResult.MAYBE && result.wasPreviouslyVisible(i) && snap.hasMesh[i]) {
                long node = snap.sectionNodes[i];
                if (node != 0) {
                    graphState.enqueue(i, node, 0);
                    result.markSurelyVisible(i);
                }
            }
        }
    }

    private void runBFS(CullingSnapshot snap) {
        while (!graphState.queueEmpty()) {
            XenoNode node = graphState.dequeue();
            if (node == null) break;

            result.markSurelyVisible(node.sectionIndex);

            for (Direction dir : DIRECTIONS) {
                if (node.hasPropagated(dir)) continue;

                long neighborNode = GraphState.getNeighborSectionNode(node.sectionNode, dir);
                int neighborIdx = GraphState.getNeighborIndex(node.sectionNode, dir, snap);

                if (neighborIdx < 0 || neighborIdx >= snap.sectionCount) continue;
                if (!snap.hasMesh[neighborIdx]) continue;

                if (snap.smartCull && node.hasAnySourceDir()) {
                    boolean visible = false;
                    for (int i = 0; i < DIRECTIONS.length; i++) {
                        if (node.hasSourceDir(i) && snap.facesCanSeeEachother(neighborIdx, DIRECTIONS[i].getOpposite().ordinal(), dir.ordinal())) {
                            visible = true;
                            break;
                        }
                    }
                    if (!visible) continue;
                }

                XenoNode existing = graphState.nodes[neighborIdx];
                if (existing.step != XenoNode.UNSET && existing.sectionIndex == neighborIdx) {
                    existing.addSourceDir(dir);
                } else {
                    graphState.enqueue(neighborIdx, neighborNode, node.step + 1);
                    graphState.nodes[neighborIdx].addSourceDir(dir);
                }

                node.markPropagated(dir);
            }
        }
    }

    private void finalizeOccluded(CullingSnapshot snap) {
        for (int i = 0; i < snap.sectionCount; i++) {
            if (result.getWriteVisibility(i) == CullingResult.MAYBE) {
                result.markOccluded(i);
            }
        }
    }

    private int getNodeIndex(int sectionX, int sectionY, int sectionZ, CullingSnapshot snap) {
        if (sectionY < snap.minSectionY || sectionY > snap.maxSectionY) return -1;

        int halfRadius = snap.sectionGridSizeXZ / 2;
        int relX = sectionX - SectionPos.x(snap.cameraSectionNode);
        int relZ = sectionZ - SectionPos.z(snap.cameraSectionNode);
        if (Math.abs(relX) > halfRadius || Math.abs(relZ) > halfRadius) return -1;

        int gridX = Math.floorMod(sectionX, snap.sectionGridSizeXZ);
        int gridY = sectionY - snap.minSectionY;
        int gridZ = Math.floorMod(sectionZ, snap.sectionGridSizeXZ);

        return (gridZ * snap.sectionGridSizeY + gridY) * snap.sectionGridSizeXZ + gridX;
    }
}
