package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import org.joml.Vector3d;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public class CullingThread extends Thread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE = SectionPos.blockToSectionCoord(60);
    private static final double CEILINGED_SECTION_DIAGONAL = Math.ceil(Math.sqrt(3.0) * 16.0);
    private static final Direction[] DIRECTIONS = Direction.values();

    private volatile CullingRequest pendingRequest;
    private volatile CullingOutput latestOutput;
    private volatile boolean needsFrustumUpdate = false;
    private volatile boolean processing = false;

    private final LongOpenHashSet emptySections = new LongOpenHashSet();
    private final List<SectionRenderDispatcher.RenderSection> occlusionVisible = new ArrayList<>(4096);

    private CullNode[] nodeArray = new CullNode[0];
    private boolean[] visited = new boolean[0];
    private final Queue<CullNode> bfsQueue = new ArrayDeque<>(1024);

    private SectionRenderDispatcher.RenderSection[] sortArray = new SectionRenderDispatcher.RenderSection[0];
    private double[] sortDistances = new double[0];

    private Octree dummyOctree;

    public CullingThread() {
        super("Xeno-CullingThread");
        this.setDaemon(true);
        this.setPriority(Thread.NORM_PRIORITY);
    }

    public void submitRequest(CullingRequest request) {
        pendingRequest = request;
        LockSupport.unpark(this);
    }

    public boolean isProcessing() {
        return processing || pendingRequest != null;
    }

    public CullingOutput getLatestOutput() {
        return latestOutput;
    }

    public boolean consumeFrustumUpdate() {
        if (needsFrustumUpdate) {
            needsFrustumUpdate = false;
            return true;
        }
        return false;
    }

    public void invalidate() {
        LockSupport.unpark(this);
    }

    public Octree getOctree() {
        return dummyOctree;
    }

    public void reset() {
        pendingRequest = null;
        latestOutput = null;
        emptySections.clear();
        occlusionVisible.clear();
        dummyOctree = null;
        LockSupport.unpark(this);
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            CullingRequest request = pendingRequest;
            if (request == null) {
                processing = false;
                LockSupport.park(this);
                continue;
            }

            processing = true;
            pendingRequest = null;

            try {
                processUpdates(request);
            } catch (Exception e) {
                LOGGER.error("Error in culling thread execution loop", e);
            }
        }
    }

    private void prepareCache(int size) {
        if (nodeArray.length < size) {
            CullNode[] newArray = new CullNode[size];
            System.arraycopy(nodeArray, 0, newArray, 0, nodeArray.length);
            for (int i = nodeArray.length; i < size; i++) {
                newArray[i] = new CullNode(null, null, 0);
            }
            nodeArray = newArray;
        }
        if (visited.length < size) {
            visited = new boolean[size];
        } else {
            java.util.Arrays.fill(visited, false);
        }
        bfsQueue.clear();
    }

    @SuppressWarnings({"ForLoopReplaceableByForEach", "ManualArrayToCollectionCopy"})
    private void processUpdates(CullingRequest request) {
        ViewArea viewArea = request.viewArea;
        if (viewArea == null) return;

        SectionRenderDispatcher.RenderSection[] sectionArray = request.sectionArray;

        emptySections.clear();
        emptySections.addAll(request.emptySections);

        prepareCache(viewArea.size());
        occlusionVisible.clear();

        if (dummyOctree == null) {
            dummyOctree = new Octree(viewArea.getCameraSectionPos(), viewArea.getViewDistance(), viewArea.sectionCount(), viewArea.minY());
        }

        initializeQueueForFullUpdate(request, bfsQueue, viewArea, sectionArray);
        runUpdates(request, bfsQueue, request.smartCull, request.viewDistance, sectionArray);

        BlockPos cameraCenter = SectionPos.of(request.cameraPos).center();
        double camX = request.cameraPos.x;
        double camY = request.cameraPos.y;
        double camZ = request.cameraPos.z;

        List<SectionRenderDispatcher.RenderSection> nearbyList = new ArrayList<>();
        int visibleCount = 0;

        for (int i = 0; i < occlusionVisible.size(); i++) {
            SectionRenderDispatcher.RenderSection section = occlusionVisible.get(i);
            AABB bb = section.getBoundingBox();

            if (request.frustum.isVisible(bb)) {
                if (sortArray.length <= visibleCount) {
                    int newSize = Math.max(sortArray.length * 2, visibleCount + 1024);

                    SectionRenderDispatcher.RenderSection[] newArr = new SectionRenderDispatcher.RenderSection[newSize];
                    System.arraycopy(sortArray, 0, newArr, 0, sortArray.length);
                    sortArray = newArr;

                    double[] newDist = new double[newSize];
                    System.arraycopy(sortDistances, 0, newDist, 0, sortDistances.length);
                    sortDistances = newDist;
                }

                sortArray[visibleCount] = section;
                if (isClose(bb, cameraCenter)) {
                    nearbyList.add(section);
                }

                double cx = (bb.minX + bb.maxX) * 0.5 - camX;
                double cy = (bb.minY + bb.maxY) * 0.5 - camY;
                double cz = (bb.minZ + bb.maxZ) * 0.5 - camZ;
                sortDistances[visibleCount] = cx * cx + cy * cy + cz * cz;

                visibleCount++;
            }
        }

        if (visibleCount > 0) {
            sortFrontToBack(sortArray, sortDistances, 0, visibleCount - 1);
        }

        List<SectionRenderDispatcher.RenderSection> visibleList = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            visibleList.add(sortArray[i]);
        }

        latestOutput = new CullingOutput(visibleList, nearbyList);
        needsFrustumUpdate = true;
    }

    private void sortFrontToBack(SectionRenderDispatcher.RenderSection[] sections, double[] distances, int left, int right) {
        if (left < right) {
            int pivotIndex = partition(sections, distances, left, right);
            sortFrontToBack(sections, distances, left, pivotIndex - 1);
            sortFrontToBack(sections, distances, pivotIndex + 1, right);
        }
    }

    private int partition(SectionRenderDispatcher.RenderSection[] sections, double[] distances, int left, int right) {
        double pivot = distances[right];
        int i = left - 1;
        for (int j = left; j < right; j++) {
            if (distances[j] <= pivot) {
                i++;
                swap(sections, distances, i, j);
            }
        }
        swap(sections, distances, i + 1, right);
        return i + 1;
    }

    private void swap(SectionRenderDispatcher.RenderSection[] sections, double[] distances, int i, int j) {
        SectionRenderDispatcher.RenderSection tempSec = sections[i];
        sections[i] = sections[j];
        sections[j] = tempSec;

        double tempDist = distances[i];
        distances[i] = distances[j];
        distances[j] = tempDist;
    }

    private boolean isClose(AABB bb, BlockPos cameraCenter) {
        return cameraCenter.getX() > bb.minX - 32
                && cameraCenter.getX() < bb.maxX + 32
                && cameraCenter.getY() > bb.minY - 32
                && cameraCenter.getY() < bb.maxY + 32
                && cameraCenter.getZ() > bb.minZ - 32
                && cameraCenter.getZ() < bb.maxZ + 32;
    }

    private void initializeQueueForFullUpdate(final CullingRequest request, final Queue<CullNode> queue, ViewArea viewArea, SectionRenderDispatcher.RenderSection[] sectionArray) {
        BlockPos cameraPosition = request.cameraBlockPos;
        long cameraSectionNode = SectionPos.asLong(cameraPosition);
        int cameraSectionY = SectionPos.y(cameraSectionNode);

        SectionRenderDispatcher.RenderSection cameraSection = getRelativeAt(
                SectionPos.x(cameraSectionNode), cameraSectionY, SectionPos.z(cameraSectionNode),
                SectionPos.x(cameraSectionNode), cameraSectionY, SectionPos.z(cameraSectionNode),
                request.viewDistance, request.minY, request.maxY, request.sizeY, request.sizeXZ,
                sectionArray
        );

        if (cameraSection == null) {
            boolean isBelowTheWorld = cameraSectionY < viewArea.minSectionY();
            int sectionY = isBelowTheWorld ? viewArea.minSectionY() : viewArea.maxSectionY();
            int viewDistance = viewArea.getViewDistance();
            List<CullNode> toSort = new ArrayList<>();
            int cameraSectionX = SectionPos.x(cameraSectionNode);
            int cameraSectionZ = SectionPos.z(cameraSectionNode);

            for (int sectionX = -viewDistance; sectionX <= viewDistance; sectionX++) {
                for (int sectionZ = -viewDistance; sectionZ <= viewDistance; sectionZ++) {
                    SectionRenderDispatcher.RenderSection renderSectionAt = getRelativeAt(
                            cameraSectionX, cameraSectionY, cameraSectionZ,
                            sectionX + cameraSectionX, sectionY, sectionZ + cameraSectionZ,
                            viewDistance, request.minY, request.maxY, request.sizeY, request.sizeXZ,
                            sectionArray
                    );
                    if (renderSectionAt != null) {
                        Direction sourceDirection = isBelowTheWorld ? Direction.UP : Direction.DOWN;

                        CullNode node = nodeArray[renderSectionAt.index];
                        node.reset(renderSectionAt, sourceDirection, 0);
                        node.setDirections(node.directions, sourceDirection);
                        if (sectionX > 0) {
                            node.setDirections(node.directions, Direction.EAST);
                        } else if (sectionX < 0) {
                            node.setDirections(node.directions, Direction.WEST);
                        }

                        if (sectionZ > 0) {
                            node.setDirections(node.directions, Direction.SOUTH);
                        } else if (sectionZ < 0) {
                            node.setDirections(node.directions, Direction.NORTH);
                        }

                        toSort.add(node);
                        visited[renderSectionAt.index] = true;
                    }
                }
            }

            toSort.sort(java.util.Comparator.comparingDouble(c -> cameraPosition.distSqr(SectionPos.of(c.section.getSectionNode()).center())));
            queue.addAll(toSort);
        } else {
            CullNode node = nodeArray[cameraSection.index];
            node.reset(cameraSection, null, 0);
            visited[cameraSection.index] = true;
            queue.add(node);
        }
    }

    private void runUpdates(
            final CullingRequest request,
            final Queue<CullNode> queue,
            final boolean smartCull,
            int viewDistance,
            SectionRenderDispatcher.RenderSection[] sectionArray
    ) {
        Vec3 cameraPos = request.cameraPos;
        SectionPos cameraSectionPos = SectionPos.of(cameraPos);
        int cameraSectionX = cameraSectionPos.x();
        int cameraSectionY = cameraSectionPos.y();
        int cameraSectionZ = cameraSectionPos.z();
        BlockPos cameraSectionCenter = cameraSectionPos.center();

        int minY = request.minY;
        int maxY = request.maxY;
        int sizeY = request.sizeY;
        int sizeXZ = request.sizeXZ;

        while (!queue.isEmpty()) {
            CullNode node = queue.poll();
            SectionRenderDispatcher.RenderSection currentSection = node.section;
            long sectionNode = currentSection.getSectionNode();

            if (!emptySections.contains(node.section.getSectionNode())) {
                occlusionVisible.add(node.section);
            } else {
                node.section.sectionMesh.compareAndSet(CompiledSectionMesh.UNCOMPILED, CompiledSectionMesh.EMPTY);
            }

            boolean distantFromCamera = Math.abs(SectionPos.x(sectionNode) - cameraSectionX) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
                    || Math.abs(SectionPos.y(sectionNode) - cameraSectionY) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
                    || Math.abs(SectionPos.z(sectionNode) - cameraSectionZ) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE;

            int sectionX = SectionPos.x(sectionNode);
            int sectionY = SectionPos.y(sectionNode);
            int sectionZ = SectionPos.z(sectionNode);

            for (Direction direction : DIRECTIONS) {
                int neighborX = sectionX + direction.getStepX();
                int neighborY = sectionY + direction.getStepY();
                int neighborZ = sectionZ + direction.getStepZ();

                SectionRenderDispatcher.RenderSection renderSectionAt = this.getRelativeAt(
                        cameraSectionX, cameraSectionY, cameraSectionZ,
                        neighborX, neighborY, neighborZ,
                        viewDistance, minY, maxY, sizeY, sizeXZ,
                        sectionArray
                );

                if (renderSectionAt != null && (!smartCull || !node.hasDirection(direction.getOpposite()))) {
                    if (smartCull && node.hasSourceDirections()) {
                        SectionMesh sectionMesh = currentSection.getSectionMesh();
                        boolean visible = false;

                        for (int i = 0; i < DIRECTIONS.length; i++) {
                            if (node.hasSourceDirection(i) && sectionMesh.facesCanSeeEachother(DIRECTIONS[i].getOpposite(), direction)) {
                                visible = true;
                                break;
                            }
                        }

                        if (!visible) {
                            continue;
                        }
                    }

                    if (smartCull && distantFromCamera) {
                        Vector3d checkPos = getCheckPos(cameraSectionCenter, sectionNode, direction);
                        Vector3d step = new Vector3d(cameraPos.x, cameraPos.y, cameraPos.z).sub(checkPos).normalize().mul(CEILINGED_SECTION_DIAGONAL);
                        boolean visible = true;

                        while (checkPos.distanceSquared(cameraPos.x, cameraPos.y, cameraPos.z) > 3600.0) {
                            checkPos.add(step);
                            if (checkPos.y > (double) 320 || checkPos.y < (double) -64) {
                                break;
                            }

                            int checkSecX = SectionPos.blockToSectionCoord(checkPos.x);
                            int checkSecY = SectionPos.blockToSectionCoord(checkPos.y);
                            int checkSecZ = SectionPos.blockToSectionCoord(checkPos.z);

                            SectionRenderDispatcher.RenderSection checkSection = getRelativeAt(
                                    cameraSectionX, cameraSectionY, cameraSectionZ,
                                    checkSecX, checkSecY, checkSecZ,
                                    viewDistance, minY, maxY, sizeY, sizeXZ,
                                    sectionArray
                            );
                            if (checkSection == null || !visited[checkSection.index]) {
                                visible = false;
                                break;
                            }
                        }

                        if (!visible) {
                            continue;
                        }
                    }

                    if (visited[renderSectionAt.index]) {
                        CullNode existingNode = nodeArray[renderSectionAt.index];
                        existingNode.addSourceDirection(direction);
                    } else {
                        visited[renderSectionAt.index] = true;
                        CullNode newNode = nodeArray[renderSectionAt.index];
                        newNode.reset(renderSectionAt, direction, node.step + 1);
                        newNode.setDirections(node.directions, direction);
                        queue.add(newNode);
                    }
                }
            }
        }
    }

    private SectionRenderDispatcher.RenderSection getRelativeAt(
            int cameraX, int cameraY, int cameraZ,
            int neighborX, int neighborY, int neighborZ,
            int viewDistance, int minY, int maxY, int sizeY, int sizeXZ,
            SectionRenderDispatcher.RenderSection[] sectionArray
    ) {
        if (neighborY < minY || neighborY > maxY) {
            return null;
        }
        if (!net.minecraft.server.level.ChunkTrackingView.isInViewDistance(cameraX, cameraZ, viewDistance, neighborX, neighborZ)) {
            return null;
        }
        if (Mth.abs(cameraY - neighborY) > viewDistance) {
            return null;
        }
        int y = neighborY - minY;
        int x = Math.floorMod(neighborX, sizeXZ);
        int z = Math.floorMod(neighborZ, sizeXZ);
        int index = (z * sizeY + y) * sizeXZ + x;
        return sectionArray[index];
    }

    private Vector3d getCheckPos(BlockPos cameraSectionCenter, long sectionNode, Direction direction) {
        int originX = SectionPos.sectionToBlockCoord(SectionPos.x(sectionNode));
        int originY = SectionPos.sectionToBlockCoord(SectionPos.y(sectionNode));
        int originZ = SectionPos.sectionToBlockCoord(SectionPos.z(sectionNode));

        boolean maxX = direction.getAxis() == Axis.X
                ? cameraSectionCenter.getX() > originX
                : cameraSectionCenter.getX() < originX;
        boolean maxY = direction.getAxis() == Axis.Y
                ? cameraSectionCenter.getY() > originY
                : cameraSectionCenter.getY() < originY;
        boolean maxZ = direction.getAxis() == Axis.Z
                ? cameraSectionCenter.getZ() > originZ
                : cameraSectionCenter.getZ() < originZ;

        return new Vector3d(
                originX + (maxX ? 16 : 0),
                originY + (maxY ? 16 : 0),
                originZ + (maxZ ? 16 : 0)
        );
    }
}