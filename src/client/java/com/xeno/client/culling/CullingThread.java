package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import net.minecraft.world.phys.AABB;

public class CullingThread extends Thread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE = SectionPos.blockToSectionCoord(60);
    private static final Direction[] DIRECTIONS = Direction.values();

    private volatile CullingRequest pendingRequest;
    private volatile CullingOutput latestOutput;
    private volatile boolean needsFrustumUpdate = false;
    private volatile boolean processing = false;

    private final LongOpenHashSet emptySections = new LongOpenHashSet();
    private final List<SectionRenderDispatcher.RenderSection> occlusionVisible = new ArrayList<>(4096);

    private CullNode[] nodeArray = new CullNode[0];
    private boolean[] visited = new boolean[0];
    private boolean[] emptyArray = new boolean[0];

    private int[] bfsQueue = new int[0];
    private int queueHead = 0;
    private int queueTail = 0;

    private int[] fallbackNodes = new int[0];
    private double[] fallbackDist = new double[0];

    private SectionRenderDispatcher.RenderSection[] sortArray = new SectionRenderDispatcher.RenderSection[0];
    private double[] sortDistances = new double[0];

    private Octree dummyOctree;

    public CullingThread() {
        super("Xeno-CullingThread");
        this.setDaemon(true);
        this.setPriority(Thread.NORM_PRIORITY);
    }

    public void submitRequest(CullingRequest request) {
        this.pendingRequest = request;
        LockSupport.unpark(this);
    }

    public boolean isProcessing() {
        return this.processing || this.pendingRequest != null;
    }

    public CullingOutput getLatestOutput() {
        return this.latestOutput;
    }

    public boolean consumeFrustumUpdate() {
        if (this.needsFrustumUpdate) {
            this.needsFrustumUpdate = false;
            return true;
        }
        return false;
    }

    public void invalidate() {
        LockSupport.unpark(this);
    }

    public Octree getOctree() {
        return this.dummyOctree;
    }

    public void reset() {
        this.pendingRequest = null;
        this.latestOutput = null;
        this.emptySections.clear();
        this.occlusionVisible.clear();
        this.dummyOctree = null;
        LockSupport.unpark(this);
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            CullingRequest request = this.pendingRequest;
            if (request == null) {
                this.processing = false;
                LockSupport.park(this);
                continue;
            }

            this.processing = true;
            this.pendingRequest = null;

            try {
                this.processUpdates(request);
            } catch (Exception e) {
                LOGGER.error("Error in culling thread execution loop", e);
            }
        }
    }

    private void prepareCache(int size) {
        if (this.nodeArray.length < size) {
            CullNode[] newArray = new CullNode[size];
            System.arraycopy(this.nodeArray, 0, newArray, 0, this.nodeArray.length);
            for (int i = this.nodeArray.length; i < size; i++) {
                newArray[i] = new CullNode(null, null, 0);
            }
            this.nodeArray = newArray;
        }
        if (this.visited.length < size) {
            this.visited = new boolean[size];
            this.emptyArray = new boolean[size];
            this.bfsQueue = new int[size];
            this.fallbackNodes = new int[size];
            this.fallbackDist = new double[size];
        } else {
            java.util.Arrays.fill(this.visited, false);
            java.util.Arrays.fill(this.emptyArray, false);
        }
        this.queueHead = 0;
        this.queueTail = 0;
    }

    @SuppressWarnings({"ForLoopReplaceableByForEach"})
    private void processUpdates(CullingRequest request) {
        ViewArea viewArea = request.viewArea;
        if (viewArea == null) return;

        SectionRenderDispatcher.RenderSection[] sectionArray = request.sectionArray;

        if (request.needsFullBfs || this.occlusionVisible.isEmpty()) {
            this.emptySections.clear();
            this.emptySections.addAll(request.emptySections);

            this.prepareCache(viewArea.size());
            this.occlusionVisible.clear();

            for (int i = 0; i < sectionArray.length; i++) {
                if (sectionArray[i] != null) {
                    this.emptyArray[sectionArray[i].index] = this.emptySections.contains(sectionArray[i].getSectionNode());
                }
            }

            if (this.dummyOctree == null) {
                this.dummyOctree = new Octree(viewArea.getCameraSectionPos(), viewArea.getViewDistance(), viewArea.sectionCount(), viewArea.minY());
            }

            this.initializeQueueForFullUpdate(request, viewArea, sectionArray);
            this.runUpdates(request, request.smartCull, request.viewDistance, sectionArray);
        }

        BlockPos cameraCenter = SectionPos.of(request.cameraPos).center();
        double camX = request.cameraPos.x;
        double camY = request.cameraPos.y;
        double camZ = request.cameraPos.z;

        List<SectionRenderDispatcher.RenderSection> nearbyList = new ArrayList<>();
        int visibleCount = 0;

        for (int i = 0; i < this.occlusionVisible.size(); i++) {
            SectionRenderDispatcher.RenderSection section = this.occlusionVisible.get(i);
            AABB bb = section.getBoundingBox();

            if (request.frustum.isVisible(bb)) {
                if (this.sortArray.length <= visibleCount) {
                    int newSize = Math.max(this.sortArray.length * 2, visibleCount + 1024);

                    SectionRenderDispatcher.RenderSection[] newArr = new SectionRenderDispatcher.RenderSection[newSize];
                    System.arraycopy(this.sortArray, 0, newArr, 0, this.sortArray.length);
                    this.sortArray = newArr;

                    double[] newDist = new double[newSize];
                    System.arraycopy(this.sortDistances, 0, newDist, 0, this.sortDistances.length);
                    this.sortDistances = newDist;
                }

                this.sortArray[visibleCount] = section;
                if (this.isClose(bb, cameraCenter)) {
                    nearbyList.add(section);
                }

                double cx = (bb.minX + bb.maxX) * 0.5 - camX;
                double cy = (bb.minY + bb.maxY) * 0.5 - camY;
                double cz = (bb.minZ + bb.maxZ) * 0.5 - camZ;
                this.sortDistances[visibleCount] = cx * cx + cy * cy + cz * cz;

                visibleCount++;
            }
        }

        if (visibleCount > 0) {
            this.sortFrontToBack(this.sortArray, this.sortDistances, 0, visibleCount - 1);
        }

        List<SectionRenderDispatcher.RenderSection> visibleList = new ArrayList<>(visibleCount);
        java.util.BitSet visibleIndices = new java.util.BitSet(viewArea.size());
        for (int i = 0; i < visibleCount; i++) {
            visibleList.add(this.sortArray[i]);
            visibleIndices.set(this.sortArray[i].index);
        }

        this.latestOutput = new CullingOutput(visibleList, nearbyList, visibleIndices);
        this.needsFrustumUpdate = true;
    }

    private void sortFrontToBack(SectionRenderDispatcher.RenderSection[] sections, double[] distances, int left, int right) {
        if (left < right) {
            int pivotIndex = this.partition(sections, distances, left, right);
            this.sortFrontToBack(sections, distances, left, pivotIndex - 1);
            this.sortFrontToBack(sections, distances, pivotIndex + 1, right);
        }
    }

    private int partition(SectionRenderDispatcher.RenderSection[] sections, double[] distances, int left, int right) {
        double pivot = distances[right];
        int i = left - 1;
        for (int j = left; j < right; j++) {
            if (distances[j] <= pivot) {
                i++;
                SectionRenderDispatcher.RenderSection tempSec = sections[i];
                sections[i] = sections[j];
                sections[j] = tempSec;
                double tempDist = distances[i];
                distances[i] = distances[j];
                distances[j] = tempDist;
            }
        }
        SectionRenderDispatcher.RenderSection tempSec = sections[i + 1];
        sections[i + 1] = sections[right];
        sections[right] = tempSec;
        double tempDist = distances[i + 1];
        distances[i + 1] = distances[right];
        distances[right] = tempDist;
        return i + 1;
    }

    private void sortFallback(int[] nodes, double[] distances, int left, int right) {
        if (left < right) {
            int pivotIndex = this.partitionFallback(nodes, distances, left, right);
            this.sortFallback(nodes, distances, left, pivotIndex - 1);
            this.sortFallback(nodes, distances, pivotIndex + 1, right);
        }
    }

    private int partitionFallback(int[] nodes, double[] distances, int left, int right) {
        double pivot = distances[right];
        int i = left - 1;
        for (int j = left; j < right; j++) {
            if (distances[j] <= pivot) {
                i++;
                int tempNode = nodes[i];
                nodes[i] = nodes[j];
                nodes[j] = tempNode;
                double tempDist = distances[i];
                distances[i] = distances[j];
                distances[j] = tempDist;
            }
        }
        int tempNode = nodes[i + 1];
        nodes[i + 1] = nodes[right];
        nodes[right] = tempNode;
        double tempDist = distances[i + 1];
        distances[i + 1] = distances[right];
        distances[right] = tempDist;
        return i + 1;
    }

    private boolean isClose(AABB bb, BlockPos cameraCenter) {
        return cameraCenter.getX() > bb.minX - 32
                && cameraCenter.getX() < bb.maxX + 32
                && cameraCenter.getY() > bb.minY - 32
                && cameraCenter.getY() < bb.maxY + 32
                && cameraCenter.getZ() > bb.minZ - 32
                && cameraCenter.getZ() < bb.maxZ + 32;
    }

    private void initializeQueueForFullUpdate(final CullingRequest request, ViewArea viewArea, SectionRenderDispatcher.RenderSection[] sectionArray) {
        BlockPos cameraPosition = request.cameraBlockPos;
        long cameraSectionNode = SectionPos.asLong(cameraPosition);
        int cameraSectionY = SectionPos.y(cameraSectionNode);

        SectionRenderDispatcher.RenderSection cameraSection = this.getRelativeAt(
                SectionPos.x(cameraSectionNode), cameraSectionY, SectionPos.z(cameraSectionNode),
                SectionPos.x(cameraSectionNode), cameraSectionY, SectionPos.z(cameraSectionNode),
                request.viewDistance, request.minY, request.maxY, request.sizeY, request.sizeXZ,
                sectionArray
        );

        if (cameraSection == null) {
            boolean isBelowTheWorld = cameraSectionY < viewArea.minSectionY();
            int sectionY = isBelowTheWorld ? viewArea.minSectionY() : viewArea.maxSectionY();
            int viewDistance = viewArea.getViewDistance();
            int cameraSectionX = SectionPos.x(cameraSectionNode);
            int cameraSectionZ = SectionPos.z(cameraSectionNode);

            int count = 0;

            for (int sectionX = -viewDistance; sectionX <= viewDistance; sectionX++) {
                for (int sectionZ = -viewDistance; sectionZ <= viewDistance; sectionZ++) {
                    SectionRenderDispatcher.RenderSection renderSectionAt = this.getRelativeAt(
                            cameraSectionX, cameraSectionY, cameraSectionZ,
                            sectionX + cameraSectionX, sectionY, sectionZ + cameraSectionZ,
                            viewDistance, request.minY, request.maxY, request.sizeY, request.sizeXZ,
                            sectionArray
                    );
                    if (renderSectionAt != null) {
                        Direction sourceDirection = isBelowTheWorld ? Direction.UP : Direction.DOWN;

                        CullNode node = this.nodeArray[renderSectionAt.index];
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

                        this.fallbackNodes[count] = renderSectionAt.index;
                        int secNodeX = SectionPos.x(renderSectionAt.getSectionNode());
                        int secNodeZ = SectionPos.z(renderSectionAt.getSectionNode());
                        double cx = SectionPos.sectionToBlockCoord(secNodeX) + 8.0 - cameraPosition.getX();
                        double cz = SectionPos.sectionToBlockCoord(secNodeZ) + 8.0 - cameraPosition.getZ();
                        this.fallbackDist[count] = cx * cx + cz * cz;
                        count++;

                        this.visited[renderSectionAt.index] = true;
                    }
                }
            }

            if (count > 0) {
                this.sortFallback(this.fallbackNodes, this.fallbackDist, 0, count - 1);
                for (int i = 0; i < count; i++) {
                    this.bfsQueue[this.queueTail++] = this.fallbackNodes[i];
                }
            }
        } else {
            CullNode node = this.nodeArray[cameraSection.index];
            node.reset(cameraSection, null, 0);
            this.visited[cameraSection.index] = true;
            this.bfsQueue[this.queueTail++] = cameraSection.index;
        }
    }

    private void runUpdates(
            final CullingRequest request,
            final boolean smartCull,
            int viewDistance,
            SectionRenderDispatcher.RenderSection[] sectionArray
    ) {
        double camX = request.cameraPos.x;
        double camY = request.cameraPos.y;
        double camZ = request.cameraPos.z;
        SectionPos cameraSectionPos = SectionPos.of(request.cameraPos);
        int cameraSectionX = cameraSectionPos.x();
        int cameraSectionY = cameraSectionPos.y();
        int cameraSectionZ = cameraSectionPos.z();

        BlockPos cameraSectionCenter = cameraSectionPos.center();
        double centerCamX = cameraSectionCenter.getX();
        double centerCamY = cameraSectionCenter.getY();
        double centerCamZ = cameraSectionCenter.getZ();

        int minY = request.minY;
        int maxY = request.maxY;
        int sizeY = request.sizeY;
        int sizeXZ = request.sizeXZ;

        while (this.queueHead < this.queueTail) {
            int nodeIndex = this.bfsQueue[this.queueHead++];
            CullNode node = this.nodeArray[nodeIndex];
            SectionRenderDispatcher.RenderSection currentSection = node.section;
            long sectionNode = currentSection.getSectionNode();

            if (!this.emptyArray[currentSection.index]) {
                this.occlusionVisible.add(currentSection);
            } else {
                currentSection.sectionMesh.compareAndSet(CompiledSectionMesh.UNCOMPILED, CompiledSectionMesh.EMPTY);
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
                        int originX = SectionPos.sectionToBlockCoord(sectionX);
                        int originY = SectionPos.sectionToBlockCoord(sectionY);
                        int originZ = SectionPos.sectionToBlockCoord(sectionZ);

                        boolean bMaxX = direction.getAxis() == Axis.X ? centerCamX > originX : centerCamX < originX;
                        boolean bMaxY = direction.getAxis() == Axis.Y ? centerCamY > originY : centerCamY < originY;
                        boolean bMaxZ = direction.getAxis() == Axis.Z ? centerCamZ > originZ : centerCamZ < originZ;

                        double checkX = originX + (bMaxX ? 16.0 : 0.0);
                        double checkY = originY + (bMaxY ? 16.0 : 0.0);
                        double checkZ = originZ + (bMaxZ ? 16.0 : 0.0);

                        double dirX = camX - checkX;
                        double dirY = camY - checkY;
                        double dirZ = camZ - checkZ;

                        double invLen = 1.0 / Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                        dirX *= invLen * 28.0;
                        dirY *= invLen * 28.0;
                        dirZ *= invLen * 28.0;

                        boolean visible = true;

                        while (true) {
                            double dX = camX - checkX;
                            double dY = camY - checkY;
                            double dZ = camZ - checkZ;

                            if (dX * dX + dY * dY + dZ * dZ <= 3600.0) {
                                break;
                            }

                            checkX += dirX;
                            checkY += dirY;
                            checkZ += dirZ;

                            if (checkY > 320.0 || checkY < -64.0) {
                                break;
                            }

                            int checkSecX = SectionPos.blockToSectionCoord(checkX);
                            int checkSecY = SectionPos.blockToSectionCoord(checkY);
                            int checkSecZ = SectionPos.blockToSectionCoord(checkZ);

                            SectionRenderDispatcher.RenderSection checkSection = this.getRelativeAt(
                                    cameraSectionX, cameraSectionY, cameraSectionZ,
                                    checkSecX, checkSecY, checkSecZ,
                                    viewDistance, minY, maxY, sizeY, sizeXZ,
                                    sectionArray
                            );

                            if (checkSection == null || !this.visited[checkSection.index]) {
                                visible = false;
                                break;
                            }
                        }

                        if (!visible) {
                            continue;
                        }
                    }

                    if (this.visited[renderSectionAt.index]) {
                        CullNode existingNode = this.nodeArray[renderSectionAt.index];
                        existingNode.addSourceDirection(direction);
                    } else {
                        this.visited[renderSectionAt.index] = true;
                        CullNode newNode = this.nodeArray[renderSectionAt.index];
                        newNode.reset(renderSectionAt, direction, node.step + 1);
                        newNode.setDirections(node.directions, direction);
                        this.bfsQueue[this.queueTail++] = renderSectionAt.index;
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
}