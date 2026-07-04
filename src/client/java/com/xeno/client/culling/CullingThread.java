package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
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

    // Thread-local culling sets/lists (completely single-threaded, no concurrent overhead)
    private final LongOpenHashSet emptySections = new LongOpenHashSet();
    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
    private final List<SectionRenderDispatcher.RenderSection> occlusionVisible = new ArrayList<>();
    
    private CullNodeMap sectionToNodeMap;
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
        loadedChunks.clear();
        occlusionVisible.clear();
        sectionToNodeMap = null;
        dummyOctree = null;
        LockSupport.unpark(this);
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            CullingRequest request = pendingRequest;
            if (request == null) {
                LockSupport.parkNanos(5_000_000L); // Sleep up to 5ms
                continue;
            }
            pendingRequest = null;

            try {
                processUpdates(request);
            } catch (Exception e) {
                LOGGER.error("Error in culling thread execution loop", e);
            }
        }
    }

    private void processUpdates(CullingRequest request) {
        ViewArea viewArea = request.viewArea();
        if (viewArea == null) return;

        Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> sectionMap = request.sectionMap();

        // 1. Copy snapshot empty sections and loaded chunks into thread-local sets
        emptySections.clear();
        emptySections.addAll(request.emptySections());
        loadedChunks.clear();
        loadedChunks.addAll(request.loadedChunks());

        // 2. Clear state and run full BFS occlusion culling
        occlusionVisible.clear();
        sectionToNodeMap = new CullNodeMap(viewArea.size());
        
        // Expose a dummy Octree to avoid NullPointerExceptions in debug renderers (F3 mode)
        if (dummyOctree == null) {
            dummyOctree = new Octree(viewArea.getCameraSectionPos(), viewArea.getViewDistance(), viewArea.sectionCount(), viewArea.minY());
        }

        Queue<CullNode> queue = new ArrayDeque<>();
        initializeQueueForFullUpdate(request.cameraBlockPos(), queue, viewArea, sectionMap);
        for (CullNode node : queue) {
            sectionToNodeMap.put(node.section, node);
        }

        runUpdates(sectionToNodeMap, queue, request.smartCull(), viewArea.getViewDistance(), sectionMap);

        // 3. Linear Frustum Culling on BFS-visible sections (No octree builder or traverser!)
        List<SectionRenderDispatcher.RenderSection> visibleList = new ArrayList<>();
        List<SectionRenderDispatcher.RenderSection> nearbyList = new ArrayList<>();
        Frustum offsetFrustum = new Frustum(request.frustum()).offsetToFullyIncludeCameraCube(8);
        BlockPos cameraCenter = SectionPos.of(request.cameraPos()).center();

        for (SectionRenderDispatcher.RenderSection section : occlusionVisible) {
            if (offsetFrustum.isVisible(section.getBoundingBox())) {
                visibleList.add(section);
                if (isClose(section.getBoundingBox(), cameraCenter)) {
                    nearbyList.add(section);
                }
            }
        }

        // Sort visible sections front-to-back by distance from camera to maximize GPU early-Z culling
        visibleList.sort(java.util.Comparator.comparingDouble(section -> {
            AABB bb = section.getBoundingBox();
            double cx = (bb.minX + bb.maxX) * 0.5;
            double cy = (bb.minY + bb.maxY) * 0.5;
            double cz = (bb.minZ + bb.maxZ) * 0.5;
            double dx = cx - request.cameraPos().x;
            double dy = cy - request.cameraPos().y;
            double dz = cz - request.cameraPos().z;
            return dx * dx + dy * dy + dz * dz;
        }));

        latestOutput = new CullingOutput(visibleList, nearbyList);
        needsFrustumUpdate = true;
    }

    private boolean isClose(AABB bb, BlockPos cameraCenter) {
        return cameraCenter.getX() > bb.minX - 32
            && cameraCenter.getX() < bb.maxX + 32
            && cameraCenter.getY() > bb.minY - 32
            && cameraCenter.getY() < bb.maxY + 32
            && cameraCenter.getZ() > bb.minZ - 32
            && cameraCenter.getZ() < bb.maxZ + 32;
    }

    private void initializeQueueForFullUpdate(final BlockPos cameraPosition, final Queue<CullNode> queue, ViewArea viewArea, Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> sectionMap) {
        long cameraSectionNode = SectionPos.asLong(cameraPosition);
        int cameraSectionY = SectionPos.y(cameraSectionNode);
        SectionRenderDispatcher.RenderSection cameraSection = sectionMap.get(cameraSectionNode);
        if (cameraSection == null) {
            boolean isBelowTheWorld = cameraSectionY < viewArea.minSectionY();
            int sectionY = isBelowTheWorld ? viewArea.minSectionY() : viewArea.maxSectionY();
            int viewDistance = viewArea.getViewDistance();
            List<CullNode> toAdd = new ArrayList<>();
            int cameraSectionX = SectionPos.x(cameraSectionNode);
            int cameraSectionZ = SectionPos.z(cameraSectionNode);

            for (int sectionX = -viewDistance; sectionX <= viewDistance; sectionX++) {
                for (int sectionZ = -viewDistance; sectionZ <= viewDistance; sectionZ++) {
                    SectionRenderDispatcher.RenderSection renderSectionAt = sectionMap.get(SectionPos.asLong(sectionX + cameraSectionX, sectionY, sectionZ + cameraSectionZ));
                    if (renderSectionAt != null && this.isInViewDistance(cameraSectionNode, renderSectionAt.getSectionNode(), viewDistance)) {
                        Direction sourceDirection = isBelowTheWorld ? Direction.UP : Direction.DOWN;
                        CullNode node = new CullNode(renderSectionAt, sourceDirection, 0);
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

                        toAdd.add(node);
                    }
                }
            }

            toAdd.sort(java.util.Comparator.comparingDouble(c -> cameraPosition.distSqr(SectionPos.of(c.section.getSectionNode()).center())));
            queue.addAll(toAdd);
        } else {
            queue.add(new CullNode(cameraSection, null, 0));
        }
    }

    private void runUpdates(
        final CullNodeMap sectionToNodeMap,
        final Queue<CullNode> queue,
        final boolean smartCull,
        int viewDistance,
        Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> sectionMap
    ) {
        Vec3 cameraPos = pendingRequest != null ? pendingRequest.cameraPos() : Vec3.ZERO;
        SectionPos cameraSectionPos = SectionPos.of(cameraPos);
        long cameraSectionNode = cameraSectionPos.asLong();
        BlockPos cameraSectionCenter = cameraSectionPos.center();

        while (!queue.isEmpty()) {
            CullNode node = queue.poll();
            SectionRenderDispatcher.RenderSection currentSection = node.section;
            long sectionNode = currentSection.getSectionNode();
            
            // Only propagate if the chunk is loaded
            long chunkNode = ChunkPos.fromSectionNode(sectionNode);
            if (loadedChunks.contains(chunkNode)) {
                if (!emptySections.contains(node.section.getSectionNode())) {
                    occlusionVisible.add(node.section);
                } else {
                    node.section.sectionMesh.compareAndSet(CompiledSectionMesh.UNCOMPILED, CompiledSectionMesh.EMPTY);
                }

                boolean distantFromCamera = Math.abs(SectionPos.x(sectionNode) - cameraSectionPos.x()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
                    || Math.abs(SectionPos.y(sectionNode) - cameraSectionPos.y()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE
                    || Math.abs(SectionPos.z(sectionNode) - cameraSectionPos.z()) > MINIMUM_ADVANCED_CULLING_SECTION_DISTANCE;

                for (Direction direction : DIRECTIONS) {
                    SectionRenderDispatcher.RenderSection renderSectionAt = this.getRelativeFrom(cameraSectionNode, currentSection, direction, viewDistance, sectionMap);
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

                                long checkNode = SectionPos.asLong(BlockPos.containing(checkPos.x, checkPos.y, checkPos.z));
                                SectionRenderDispatcher.RenderSection checkSection = sectionMap.get(checkNode);
                                if (checkSection == null || sectionToNodeMap.get(checkSection) == null) {
                                    visible = false;
                                    break;
                                }
                            }

                            if (!visible) {
                                continue;
                            }
                        }

                        CullNode existingNode = sectionToNodeMap.get(renderSectionAt);
                        if (existingNode != null) {
                            existingNode.addSourceDirection(direction);
                        } else {
                            CullNode newNode = new CullNode(renderSectionAt, direction, node.step + 1);
                            newNode.setDirections(node.directions, direction);
                            queue.add(newNode);
                            sectionToNodeMap.put(renderSectionAt, newNode);
                        }
                    }
                }
            }
        }
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

    private boolean isInViewDistance(final long cameraSectionNode, final long sectionNode, int viewDistance) {
        return net.minecraft.server.level.ChunkTrackingView.isInViewDistance(
            SectionPos.x(cameraSectionNode),
            SectionPos.z(cameraSectionNode),
            viewDistance,
            SectionPos.x(sectionNode),
            SectionPos.z(sectionNode)
        );
    }

    private SectionRenderDispatcher.RenderSection getRelativeFrom(
        final long cameraSectionNode, final SectionRenderDispatcher.RenderSection renderSection, final Direction direction, int viewDistance, Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection> sectionMap
    ) {
        long relative = renderSection.getNeighborSectionNode(direction);
        if (!this.isInViewDistance(cameraSectionNode, relative, viewDistance)) {
            return null;
        } else {
            return Mth.abs(SectionPos.y(cameraSectionNode) - SectionPos.y(relative)) > viewDistance
                ? null
                : sectionMap.get(relative);
        }
    }
}
