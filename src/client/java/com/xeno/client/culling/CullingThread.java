package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public class CullingThread extends Thread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Direction[] DIRECTIONS = Direction.values();

    public static volatile double profiledLatencyMs = 0.0;
    public static volatile double displayedLatencyMs = 0.0;
    public static volatile double profiledUsagePercent = 0.0;

    private volatile CullingRequest pendingRequest;
    private volatile CullingOutput latestOutput;
    private volatile boolean needsFrustumUpdate = false;
    private volatile boolean processing = false;

    private final LongOpenHashSet emptySections = new LongOpenHashSet();
    private final List<SectionRenderDispatcher.RenderSection> occlusionVisible = new ArrayList<>(4096);

    private java.lang.foreign.Arena cullingArena;
    private java.lang.foreign.MemorySegment nodeSegment = java.lang.foreign.MemorySegment.NULL;
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
        long lastWindowStart = System.nanoTime();
        long totalActiveTimeInWindow = 0L;
        long lastLatencyUpdate = 0L;

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
                long startTime = System.nanoTime();
                this.processUpdates(request);
                long endTime = System.nanoTime();
                long duration = endTime - startTime;
                totalActiveTimeInWindow += duration;

                double currentLatency = duration / 1_000_000.0;
                profiledLatencyMs = currentLatency;

                long now = System.currentTimeMillis();
                if (now - lastLatencyUpdate >= 200L) {
                    displayedLatencyMs = currentLatency;
                    lastLatencyUpdate = now;
                }

                long elapsedSinceWindowStart = endTime - lastWindowStart;
                if (elapsedSinceWindowStart >= 500_000_000L) { // 500ms window
                    profiledUsagePercent = ((double) totalActiveTimeInWindow / elapsedSinceWindowStart) * 100.0;
                    totalActiveTimeInWindow = 0L;
                    lastWindowStart = endTime;
                }
            } catch (Exception e) {
                LOGGER.error("Error in culling thread execution loop", e);
            }
        }
    }

    private void prepareCache(int size) {
        if (this.cullingArena == null) {
            this.cullingArena = java.lang.foreign.Arena.ofConfined();
        }
        long requiredBytes = size * 8L;
        if (this.nodeSegment == java.lang.foreign.MemorySegment.NULL || this.nodeSegment.byteSize() < requiredBytes) {
            this.nodeSegment = this.cullingArena.allocate(requiredBytes, 8);
            this.visited = new boolean[size];
            this.emptyArray = new boolean[size];
            this.bfsQueue = new int[size];
            this.fallbackNodes = new int[size];
            this.fallbackDist = new double[size];
        } else {
            this.nodeSegment.fill((byte) 0);
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
        System.out.println("Xeno: occlusionVisible size = " + this.occlusionVisible.size());

        for (int i = 0; i < this.occlusionVisible.size(); i++) {
            SectionRenderDispatcher.RenderSection section = this.occlusionVisible.get(i);
            AABB bb = section.getBoundingBox();

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

        if (visibleCount > 0) {
            this.sortFrontToBack(this.sortArray, this.sortDistances, 0, visibleCount - 1);
        }

        List<SectionRenderDispatcher.RenderSection> visibleList = new ArrayList<>(visibleCount);
        List<SectionRenderDispatcher.RenderSection> solidList = new ArrayList<>();
        List<SectionRenderDispatcher.RenderSection> cutoutList = new ArrayList<>();
        List<SectionRenderDispatcher.RenderSection> translucentList = new ArrayList<>();
        java.util.BitSet visibleIndices = new java.util.BitSet(viewArea.size());

        for (int i = 0; i < visibleCount; i++) {
            SectionRenderDispatcher.RenderSection section = this.sortArray[i];
            visibleList.add(section);
            visibleIndices.set(section.index);

            SectionMesh mesh = section.getSectionMesh();
            if (mesh instanceof CompiledSectionMesh compiled) {
                if (compiled.getSectionDraw(ChunkSectionLayer.SOLID) != null) {
                    solidList.add(section);
                }
                if (compiled.getSectionDraw(ChunkSectionLayer.CUTOUT) != null) {
                    cutoutList.add(section);
                }
                if (compiled.getSectionDraw(ChunkSectionLayer.TRANSLUCENT) != null) {
                    translucentList.add(section);
                }
            } else {
                solidList.add(section);
                cutoutList.add(section);
                translucentList.add(section);
            }
        }

        this.latestOutput = new CullingOutput(visibleList, solidList, cutoutList, translucentList, nearbyList, visibleIndices);
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
                        long offset = renderSectionAt.index * 8L;

                        byte srcDir = (byte) (1 << sourceDirection.ordinal());
                        this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, offset + 1L, srcDir);
                        this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_SHORT, offset + 2L, (short) 0);

                        byte dirs = srcDir;
                        if (sectionX > 0) {
                            dirs |= (byte) (1 << Direction.EAST.ordinal());
                        } else if (sectionX < 0) {
                            dirs |= (byte) (1 << Direction.WEST.ordinal());
                        }

                        if (sectionZ > 0) {
                            dirs |= (byte) (1 << Direction.SOUTH.ordinal());
                        } else if (sectionZ < 0) {
                            dirs |= (byte) (1 << Direction.NORTH.ordinal());
                        }
                        this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, offset, dirs);

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
            long offset = cameraSection.index * 8L;
            this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, offset, (byte) 0);
            this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, offset + 1L, (byte) 0);
            this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_SHORT, offset + 2L, (short) 0);
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
        SectionPos cameraSectionPos = SectionPos.of(request.cameraPos);
        int cameraSectionX = cameraSectionPos.x();
        int cameraSectionY = cameraSectionPos.y();
        int cameraSectionZ = cameraSectionPos.z();

        int minY = request.minY;
        int maxY = request.maxY;
        int sizeY = request.sizeY;
        int sizeXZ = request.sizeXZ;

        while (this.queueHead < this.queueTail) {
            int nodeIndex = this.bfsQueue[this.queueHead++];
            SectionRenderDispatcher.RenderSection currentSection = sectionArray[nodeIndex];
            long sectionNode = currentSection.getSectionNode();
            long nodeOffset = nodeIndex * 8L;

            if (!this.emptyArray[currentSection.index]) {
                this.occlusionVisible.add(currentSection);
            } else {
                currentSection.sectionMesh.compareAndSet(CompiledSectionMesh.UNCOMPILED, CompiledSectionMesh.EMPTY);
            }

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

                if (renderSectionAt != null) {
                    byte parentDirs = this.nodeSegment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, nodeOffset);
                    boolean hasOppositeDir = (parentDirs & (1 << direction.getOpposite().ordinal())) != 0;

                    if (!smartCull || !hasOppositeDir) {
                        if (smartCull) {
                            byte parentSrcDirs = this.nodeSegment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, nodeOffset + 1L);
                            if (parentSrcDirs != 0) {
                                SectionMesh sectionMesh = currentSection.getSectionMesh();
                                boolean visible = false;

                                for (int i = 0; i < DIRECTIONS.length; i++) {
                                    boolean hasSrcDir = (parentSrcDirs & (1 << i)) != 0;
                                    if (hasSrcDir && sectionMesh.facesCanSeeEachother(DIRECTIONS[i].getOpposite(), direction)) {
                                        visible = true;
                                        break;
                                    }
                                }

                                if (!visible) {
                                    continue;
                                }
                            }
                        }

                        if (this.visited[renderSectionAt.index]) {
                            long neighborOffset = renderSectionAt.index * 8L;
                            byte oldSrc = this.nodeSegment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, neighborOffset + 1L);
                            this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, neighborOffset + 1L, (byte) (oldSrc | (1 << direction.ordinal())));
                        } else {
                            this.visited[renderSectionAt.index] = true;
                            long neighborOffset = renderSectionAt.index * 8L;
                            short parentStep = this.nodeSegment.get(java.lang.foreign.ValueLayout.JAVA_SHORT, nodeOffset + 2L);

                            byte newDirs = (byte) (parentDirs | (1 << direction.ordinal()));
                            byte newSrcDirs = (byte) (1 << direction.ordinal());

                            this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, neighborOffset, newDirs);
                            this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, neighborOffset + 1L, newSrcDirs);
                            this.nodeSegment.set(java.lang.foreign.ValueLayout.JAVA_SHORT, neighborOffset + 2L, (short) (parentStep + 1));

                            this.bfsQueue[this.queueTail++] = renderSectionAt.index;
                        }
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

        int x = neighborX - cameraX + viewDistance;
        int z = neighborZ - cameraZ + viewDistance;
        int y = neighborY - minY;

        if (x < 0 || x >= sizeXZ || z < 0 || z >= sizeXZ || y < 0 || y >= sizeY) {
            return null;
        }
        int index = (z * sizeY + y) * sizeXZ + x;
        System.out.println("Xeno: found section at index " + index);
        return sectionArray[index];
    }
}