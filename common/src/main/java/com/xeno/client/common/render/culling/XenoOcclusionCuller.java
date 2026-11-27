package com.xeno.client.common.render.culling;

import com.xeno.client.common.render.texture.XenoCompiledSectionMeshExtension;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class XenoOcclusionCuller {
    private static final long FULL_VISIBILITY_MASK = XenoDirectionalVisGraph.FULL_VISIBILITY_MASK;
    private static final double SECTION_HALF_DIAGONAL = Math.sqrt(192.0);
    private static final double RAY_STEP_DIVISOR = SECTION_HALF_DIAGONAL * 2.0;
    private static final int RAY_TEST_MAX_STEPS = 8;
    private static final double RAY_MIN_DIST_SQ = 64.0 * 64.0;
    private static final float WIDE_ANGLE_MARGIN = 32.0f;
    private static final int NEARBY_PADDING = 32;
    private static final int IDLE_SPIN_COUNT = 128;

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int[] DIR_DX = new int[6];
    private static final int[] DIR_DY = new int[6];
    private static final int[] DIR_DZ = new int[6];
    private static final long UP_DOWN_MASK;
    private static final long NORTH_SOUTH_MASK;
    private static final long WEST_EAST_MASK;

    static {
        for (int i = 0; i < 6; i++) {
            Direction d = DIRECTIONS[i];
            DIR_DX[i] = d.getStepX();
            DIR_DY[i] = d.getStepY();
            DIR_DZ[i] = d.getStepZ();
        }
        UP_DOWN_MASK = (1L << (Direction.UP.ordinal() * 6 + Direction.UP.ordinal()))
                | (1L << (Direction.DOWN.ordinal() * 6 + Direction.DOWN.ordinal()));
        NORTH_SOUTH_MASK = (1L << (Direction.NORTH.ordinal() * 6 + Direction.NORTH.ordinal()))
                | (1L << (Direction.SOUTH.ordinal() * 6 + Direction.SOUTH.ordinal()));
        WEST_EAST_MASK = (1L << (Direction.WEST.ordinal() * 6 + Direction.WEST.ordinal()))
                | (1L << (Direction.EAST.ordinal() * 6 + Direction.EAST.ordinal()));
    }

    public static final XenoOcclusionCuller INSTANCE = new XenoOcclusionCuller();

    public record GridLayout(
            RenderSection[] sections,
            int viewDistance,
            int minSectionY,
            int maxSectionY,
            int minBlockY,
            int maxBlockY,
            int gridSizeY,
            int gridSizeXZ
    ) {
        public int indexFor(int secX, int secY, int secZ, int centerSecX, int centerSecZ) {
            if (secY < this.minSectionY || secY > this.maxSectionY) {
                return -1;
            }
            if (secX < centerSecX - this.viewDistance || secX > centerSecX + this.viewDistance) {
                return -1;
            }
            if (secZ < centerSecZ - this.viewDistance || secZ > centerSecZ + this.viewDistance) {
                return -1;
            }
            int modX = Math.floorMod(secX, this.gridSizeXZ);
            int modZ = Math.floorMod(secZ, this.gridSizeXZ);
            int yOff = secY - this.minSectionY;
            return (modZ * this.gridSizeY + yOff) * this.gridSizeXZ + modX;
        }
    }

    private record ChunkDelta(
            long generation,
            long @Nullable [] addedLoaded,
            long @Nullable [] removedLoaded,
            long @Nullable [] addedEmpty,
            long @Nullable [] removedEmpty
    ) {
    }

    private record PropagationEvent(
            long generation,
            RenderSection section
    ) {
    }

    private record LoadedExpectedEvent(
            long generation,
            long[] chunks
    ) {
    }

    private record OcclusionRequest(
            long seq,
            long generation,
            long localCameraSeq,
            boolean forceWideFull,
            boolean forceLocalFull,
            double camX,
            double camY,
            double camZ,
            int camBlockX,
            int camBlockY,
            int camBlockZ,
            int camSecX,
            int camSecY,
            int camSecZ,
            int centerSecX,
            int centerSecZ,
            boolean smartCull,
            @Nullable XenoBranchlessFrustum localFrustum,
            GridLayout grid
    ) {
    }

    private record CullSnapshot(
            long generation,
            long localCameraSeq,
            int camSecX,
            int camSecY,
            int camSecZ,
            XenoOctreeForest forest,
            LongCollection waitingChunks
    ) {
    }

    private final AtomicLong worldGeneration = new AtomicLong(0L);
    private final AtomicLong requestSeq = new AtomicLong(0L);
    private final AtomicLong fullRequestSeq = new AtomicLong(0L);
    private final AtomicLong validLocalFromSeq = new AtomicLong(0L);
    private final AtomicReference<@Nullable OcclusionRequest> latestRequest = new AtomicReference<>();
    private final AtomicBoolean needsFrustumUpdate = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<ChunkDelta> chunkDeltaQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PropagationEvent> propagationQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LoadedExpectedEvent> loadedExpectedQueue = new ConcurrentLinkedQueue<>();

    private volatile @Nullable GridLayout activeGrid;
    private volatile @Nullable CullSnapshot activeWideSnapshot;
    private volatile @Nullable CullSnapshot activeLocalSnapshot;
    private volatile @Nullable XenoOctreeForest renderThreadReadingForest;
    private @Nullable XenoOctreeForest frameEntityForest;
    private volatile boolean pendingWorldClear;
    private volatile boolean syncFallbackActive;
    private volatile boolean workerBusy;
    private volatile long emaPassNanos = -1L;

    private final XenoOctreeForest @Nullable [] wideForestPool = new XenoOctreeForest[4];
    private final XenoOctreeForest @Nullable [] localForestPool = new XenoOctreeForest[4];

    private final LongOpenHashSet workerLoadedChunks = new LongOpenHashSet();
    private final LongOpenHashSet workerEmptySections = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<LongArrayList> workerWaitingByChunk = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet workerWaitingChunks = new LongOpenHashSet();
    private final IntArrayList deltaWokenIndices = new IntArrayList();

    private boolean workerHasValidWideGraph;
    private boolean workerHasValidLocalGraph;
    private long workerGraphGeneration = -1L;
    private int workerGraphCamSecX;
    private int workerGraphCamSecY;
    private int workerGraphCamSecZ;
    private int workerGraphCenterSecX;
    private int workerGraphCenterSecZ;
    private boolean workerGraphSmartCull;

    private int wideEpoch = 1;
    private int localEpoch = 1;
    private int queueToken = 1;
    private int gridStateEpoch = 1;

    private int[] readQueue = new int[0];
    private int[] writeQueue = new int[0];
    private int[] queuedToken = new int[0];
    private int[] nodeWideEpoch = new int[0];
    private int[] nodeLocalEpoch = new int[0];
    private long[] wideNodeLong = new long[0];
    private long[] localNodeLong = new long[0];
    private byte[] incomingWide = new byte[0];
    private byte[] processedWide = new byte[0];
    private byte[] incomingLocal = new byte[0];
    private byte[] processedLocal = new byte[0];
    private long[] cachedMaskUnion = new long[0];
    private long[] widePortalBitset = new long[0];
    private long[] localPortalBitset = new long[0];
    private int[] wideEmittedIndices = new int[0];
    private int[] wideEmittedEpoch = new int[0];
    private int[] localEmittedIndices = new int[0];
    private int[] localEmittedEpoch = new int[0];
    private int wideEmittedCount;
    private int localEmittedCount;

    private int[] emptyCacheEpoch = new int[0];
    private long[] emptyCacheNode = new long[0];
    private boolean[] emptyCacheFlag = new boolean[0];

    private int[] colCacheEpoch = new int[0];
    private long[] colCacheChunkPos = new long[0];
    private boolean[] colCacheLoaded = new boolean[0];

    private final Thread workerThread;

    private XenoOcclusionCuller() {
        this.workerThread = new Thread(this::workerLoop, "Xeno-Occlusion-Worker");
        this.workerThread.setDaemon(true);
        this.workerThread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
        this.workerThread.start();
    }

    public void reset(@Nullable ViewArea viewArea, @Nullable RotatingSectionStorage<RenderSection> storage) {
        this.worldGeneration.incrementAndGet();
        this.latestRequest.set(null);
        this.activeWideSnapshot = null;
        this.activeLocalSnapshot = null;
        this.frameEntityForest = null;
        this.renderThreadReadingForest = null;
        this.pendingWorldClear = true;
        this.chunkDeltaQueue.clear();
        this.propagationQueue.clear();
        this.loadedExpectedQueue.clear();
        this.needsFrustumUpdate.set(true);

        if (viewArea == null || storage == null) {
            this.activeGrid = null;
            return;
        }

        int total = storage.size();
        RenderSection[] gridSections = new RenderSection[total];
        storage.forEach(section -> {
            if (section != null && section.index >= 0 && section.index < total) {
                gridSections[section.index] = section;
            }
        });

        int viewDistance = viewArea.getViewDistance();
        int minSecY = viewArea.minSectionY();
        int maxSecY = viewArea.maxSectionY();
        this.activeGrid = new GridLayout(
                gridSections,
                viewDistance,
                minSecY,
                maxSecY,
                viewArea.minY(),
                viewArea.maxY(),
                maxSecY - minSecY + 1,
                viewDistance * 2 + 1
        );
    }

    public void invalidateLocalSnapshot(long minValidLocalSeq) {
        this.validLocalFromSeq.set(minValidLocalSeq);
        this.activeLocalSnapshot = null;
    }

    public void enqueueChunkDelta(
            LongOpenHashSet addedLoaded,
            LongOpenHashSet removedLoaded,
            LongOpenHashSet addedEmpty,
            LongOpenHashSet removedEmpty
    ) {
        long gen = this.worldGeneration.get();
        long[] aLoaded = addedLoaded.isEmpty() ? null : addedLoaded.toLongArray();
        long[] rLoaded = removedLoaded.isEmpty() ? null : removedLoaded.toLongArray();
        long[] aEmpty = addedEmpty.isEmpty() ? null : addedEmpty.toLongArray();
        long[] rEmpty = removedEmpty.isEmpty() ? null : removedEmpty.toLongArray();
        if (aLoaded != null || rLoaded != null || aEmpty != null || rEmpty != null) {
            this.chunkDeltaQueue.add(new ChunkDelta(gen, aLoaded, rLoaded, aEmpty, rEmpty));
        }
    }

    public void enqueuePropagation(RenderSection section) {
        this.propagationQueue.add(new PropagationEvent(this.worldGeneration.get(), section));
    }

    public void enqueueLoadedExpectedChunks(LongOpenHashSet loadedExpected) {
        if (!loadedExpected.isEmpty()) {
            this.loadedExpectedQueue.add(new LoadedExpectedEvent(this.worldGeneration.get(), loadedExpected.toLongArray()));
        }
    }

    public void submitRequest(
            boolean forceWideFull,
            boolean forceLocalFull,
            boolean abortInFlight,
            long localCameraSeq,
            double camX,
            double camY,
            double camZ,
            int camBlockX,
            int camBlockY,
            int camBlockZ,
            boolean smartCull,
            @Nullable XenoBranchlessFrustum localFrustum,
            SectionPos cameraCenterPos
    ) {
        GridLayout grid = this.activeGrid;
        if (grid == null) {
            return;
        }
        long gen = this.worldGeneration.get();
        long seq = this.requestSeq.incrementAndGet();
        if (abortInFlight) {
            this.fullRequestSeq.set(seq);
        }
        OcclusionRequest req = new OcclusionRequest(
                seq,
                gen,
                localCameraSeq,
                forceWideFull,
                forceLocalFull,
                camX,
                camY,
                camZ,
                camBlockX,
                camBlockY,
                camBlockZ,
                SectionPos.blockToSectionCoord(camBlockX),
                SectionPos.blockToSectionCoord(camBlockY),
                SectionPos.blockToSectionCoord(camBlockZ),
                cameraCenterPos.x(),
                cameraCenterPos.z(),
                smartCull,
                localFrustum,
                grid
        );
        this.latestRequest.set(req);
        LockSupport.unpark(this.workerThread);
    }

    public boolean consumeFrustumUpdate() {
        return this.needsFrustumUpdate.compareAndSet(true, false);
    }

    public LongCollection expectedChunks() {
        CullSnapshot wide = this.activeWideSnapshot;
        if (wide != null && wide.generation == this.worldGeneration.get()) {
            return wide.waitingChunks;
        }
        CullSnapshot local = this.activeLocalSnapshot;
        if (local != null && local.generation == this.worldGeneration.get()) {
            return local.waitingChunks;
        }
        return LongSets.EMPTY_SET;
    }

    public void collectVisibleSections(
            Frustum frustum,
            boolean forceSyncFallback,
            int camSecX,
            int camSecY,
            int camSecZ,
            int camBlockX,
            int camBlockY,
            int camBlockZ,
            @Nullable List<RenderSection> outVisible,
            @Nullable List<RenderSection> outNearby
    ) {
        this.syncFallbackActive = forceSyncFallback;
        this.frameEntityForest = null;
        this.renderThreadReadingForest = null;
        long gen = this.worldGeneration.get();
        XenoBranchlessFrustum bf = frustum instanceof XenoFrustumProvider provider
                ? provider.xeno$getBranchlessFrustum()
                : null;

        if (!forceSyncFallback) {
            CullSnapshot local;
            while (true) {
                local = this.activeLocalSnapshot;
                if (local == null) {
                    break;
                }
                this.renderThreadReadingForest = local.forest;
                if (local == this.activeLocalSnapshot) {
                    break;
                }
            }
            if (local != null
                    && local.generation == gen
                    && local.localCameraSeq >= this.validLocalFromSeq.get()
                    && local.camSecX == camSecX
                    && local.camSecY == camSecY
                    && local.camSecZ == camSecZ) {
                this.frameEntityForest = local.forest;
                local.forest.traverse(frustum, bf, camSecX, camSecY, camSecZ, camBlockX, camBlockY, camBlockZ, outVisible, outNearby);
                return;
            }
            this.renderThreadReadingForest = null;

            CullSnapshot wide;
            while (true) {
                wide = this.activeWideSnapshot;
                if (wide == null) {
                    break;
                }
                this.renderThreadReadingForest = wide.forest;
                if (wide == this.activeWideSnapshot) {
                    break;
                }
            }
            if (wide != null
                    && wide.generation == gen
                    && Math.abs(camSecX - wide.camSecX) <= 1
                    && Math.abs(camSecY - wide.camSecY) <= 1
                    && Math.abs(camSecZ - wide.camSecZ) <= 1) {
                this.frameEntityForest = wide.forest;
                wide.forest.traverse(frustum, bf, camSecX, camSecY, camSecZ, camBlockX, camBlockY, camBlockZ, outVisible, outNearby);
                return;
            }
            this.renderThreadReadingForest = null;
        }

        GridLayout grid = this.activeGrid;
        if (grid == null) {
            return;
        }
        this.collectSyncFallback(grid, frustum, bf, camBlockX, camBlockY, camBlockZ, outVisible, outNearby);
    }

    private void collectSyncFallback(
            GridLayout grid,
            Frustum frustum,
            @Nullable XenoBranchlessFrustum bf,
            int camBlockX,
            int camBlockY,
            int camBlockZ,
            @Nullable List<RenderSection> outVisible,
            @Nullable List<RenderSection> outNearby
    ) {
        RenderSection[] sections = grid.sections();
        float bfCamX = bf != null ? (float) bf.getCamX() : 0.0f;
        float bfCamY = bf != null ? (float) bf.getCamY() : 0.0f;
        float bfCamZ = bf != null ? (float) bf.getCamZ() : 0.0f;

        for (int i = 0; i < sections.length; i++) {
            RenderSection section = sections[i];
            if (section == null) {
                continue;
            }
            SectionMesh mesh = section.getSectionMesh();
            if (mesh == CompiledSectionMesh.EMPTY) {
                continue;
            }
            BlockPos origin = section.getRenderOrigin();
            int ox = origin.getX();
            int oy = origin.getY();
            int oz = origin.getZ();

            boolean visible;
            if (bf != null) {
                if (mesh instanceof XenoCompiledSectionMeshExtension ext && ext.xeno$hasTightBounds()) {
                    visible = bf.isVisibleRel(
                            ox + ext.xeno$minX() - bfCamX,
                            oy + ext.xeno$minY() - bfCamY,
                            oz + ext.xeno$minZ() - bfCamZ,
                            ox + ext.xeno$maxX() - bfCamX,
                            oy + ext.xeno$maxY() - bfCamY,
                            oz + ext.xeno$maxZ() - bfCamZ
                    );
                } else {
                    visible = bf.isSectionVisibleRel(ox + 8.0f - bfCamX, oy + 8.0f - bfCamY, oz + 8.0f - bfCamZ);
                }
            } else {
                visible = frustum.isVisible(section.getBoundingBox());
            }

            if (visible) {
                if (outVisible != null) {
                    outVisible.add(section);
                }
                if (outNearby != null
                        && camBlockX > ox - NEARBY_PADDING && camBlockX < ox + 16 + NEARBY_PADDING
                        && camBlockY > oy - NEARBY_PADDING && camBlockY < oy + 16 + NEARBY_PADDING
                        && camBlockZ > oz - NEARBY_PADDING && camBlockZ < oz + 16 + NEARBY_PADDING) {
                    outNearby.add(section);
                }
            }
        }
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        XenoOctreeForest forest = this.frameEntityForest;
        if (forest == null || this.syncFallbackActive) {
            return true;
        }
        return forest.isBoxReachable(x1, y1, z1, x2, y2, z2);
    }

    public boolean isWorkerBusy() {
        return this.workerBusy;
    }

    public long getAveragePassNanos() {
        return this.emaPassNanos;
    }

    public int getUnoccludedCount() {
        long gen = this.worldGeneration.get();
        CullSnapshot local = this.activeLocalSnapshot;
        if (local != null && local.generation == gen && local.localCameraSeq >= this.validLocalFromSeq.get()) {
            return local.forest.getSectionCount();
        }
        CullSnapshot wide = this.activeWideSnapshot;
        if (wide != null && wide.generation == gen) {
            return wide.forest.getSectionCount();
        }
        return 0;
    }

    private void workerLoop() {
        long lastSeq = 0L;
        while (!Thread.currentThread().isInterrupted()) {
            long currentSeq = this.requestSeq.get();
            if (currentSeq == lastSeq) {
                boolean arrived = false;
                for (int spin = 0; spin < IDLE_SPIN_COUNT; spin++) {
                    Thread.onSpinWait();
                    currentSeq = this.requestSeq.get();
                    if (currentSeq != lastSeq) {
                        arrived = true;
                        break;
                    }
                }
                if (!arrived) {
                    LockSupport.park();
                    continue;
                }
            }

            OcclusionRequest req = this.latestRequest.get();
            if (req == null) {
                lastSeq = currentSeq;
                continue;
            }

            lastSeq = req.seq();
            this.workerBusy = true;
            long startNs = System.nanoTime();
            try {
                this.processRequest(req);
            } finally {
                long elapsed = System.nanoTime() - startNs;
                long prevEma = this.emaPassNanos;
                this.emaPassNanos = prevEma < 0L ? elapsed : (long) (prevEma * 0.8 + elapsed * 0.2);
                this.workerBusy = false;
            }
        }
    }

    private boolean drainChunkDeltas(
            long expectedGen,
            @Nullable GridLayout grid,
            int centerSecX,
            int centerSecZ,
            boolean wakeIncremental,
            IntArrayList wokenIndices
    ) {
        if (this.pendingWorldClear) {
            this.pendingWorldClear = false;
            this.workerLoadedChunks.clear();
            this.workerEmptySections.clear();
            this.workerWaitingByChunk.clear();
            this.workerWaitingChunks.clear();
            this.workerHasValidWideGraph = false;
            this.workerHasValidLocalGraph = false;
            this.advanceGridStateEpoch(grid != null ? grid.sections().length : 0, grid != null ? grid.gridSizeXZ() * grid.gridSizeXZ() : 0);
        }

        boolean requiresFull = false;
        int gridSizeXZ = grid != null ? grid.gridSizeXZ() : 0;
        ChunkDelta delta;
        while ((delta = this.chunkDeltaQueue.poll()) != null) {
            if (delta.generation() != expectedGen) {
                continue;
            }
            if (delta.addedLoaded() != null) {
                for (long c : delta.addedLoaded()) {
                    this.workerLoadedChunks.add(c);
                    if (gridSizeXZ > 0) {
                        int slot = Math.floorMod(ChunkPos.getZ(c), gridSizeXZ) * gridSizeXZ + Math.floorMod(ChunkPos.getX(c), gridSizeXZ);
                        if (this.colCacheEpoch[slot] == this.gridStateEpoch && this.colCacheChunkPos[slot] == c) {
                            this.colCacheLoaded[slot] = true;
                        }
                    }
                    if (wakeIncremental && grid != null && this.workerWaitingChunks.remove(c)) {
                        LongArrayList waiting = this.workerWaitingByChunk.remove(c);
                        if (waiting != null) {
                            for (int i = 0, n = waiting.size(); i < n; i++) {
                                long sNode = waiting.getLong(i);
                                int idx = grid.indexFor(
                                        SectionPos.x(sNode),
                                        SectionPos.y(sNode),
                                        SectionPos.z(sNode),
                                        centerSecX,
                                        centerSecZ
                                );
                                if (idx >= 0 && this.nodeWideEpoch[idx] == this.wideEpoch && this.wideNodeLong[idx] == sNode) {
                                    wokenIndices.add(idx);
                                }
                            }
                        }
                    }
                }
            }
            if (delta.removedLoaded() != null) {
                for (long c : delta.removedLoaded()) {
                    if (this.workerLoadedChunks.remove(c)) {
                        requiresFull = true;
                        if (gridSizeXZ > 0) {
                            int slot = Math.floorMod(ChunkPos.getZ(c), gridSizeXZ) * gridSizeXZ + Math.floorMod(ChunkPos.getX(c), gridSizeXZ);
                            if (this.colCacheEpoch[slot] == this.gridStateEpoch && this.colCacheChunkPos[slot] == c) {
                                this.colCacheLoaded[slot] = false;
                            }
                        }
                    }
                }
            }
            if (delta.addedEmpty() != null) {
                for (long s : delta.addedEmpty()) {
                    if (this.workerEmptySections.add(s)) {
                        requiresFull = true;
                        if (grid != null) {
                            int idx = grid.indexFor(SectionPos.x(s), SectionPos.y(s), SectionPos.z(s), centerSecX, centerSecZ);
                            if (idx >= 0 && this.emptyCacheEpoch[idx] == this.gridStateEpoch && this.emptyCacheNode[idx] == s) {
                                this.emptyCacheFlag[idx] = true;
                            }
                        }
                    }
                }
            }
            if (delta.removedEmpty() != null) {
                for (long s : delta.removedEmpty()) {
                    this.workerEmptySections.remove(s);
                    if (grid != null) {
                        int idx = grid.indexFor(
                                SectionPos.x(s),
                                SectionPos.y(s),
                                SectionPos.z(s),
                                centerSecX,
                                centerSecZ
                        );
                        if (idx >= 0) {
                            if (this.emptyCacheEpoch[idx] == this.gridStateEpoch && this.emptyCacheNode[idx] == s) {
                                this.emptyCacheFlag[idx] = false;
                            }
                            if (wakeIncremental && this.nodeWideEpoch[idx] == this.wideEpoch && this.wideNodeLong[idx] == s) {
                                this.processedWide[idx] = 0;
                                if (this.nodeLocalEpoch[idx] == this.localEpoch && this.localNodeLong[idx] == s) {
                                    this.processedLocal[idx] = 0;
                                }
                                wokenIndices.add(idx);
                            }
                        }
                    }
                }
            }
        }
        return requiresFull;
    }

    private void ensureWorkerCapacity(int totalSections, int gridCols) {
        if (this.readQueue.length < totalSections) {
            this.readQueue = new int[totalSections];
            this.writeQueue = new int[totalSections];
            this.queuedToken = new int[totalSections];
            this.nodeWideEpoch = new int[totalSections];
            this.nodeLocalEpoch = new int[totalSections];
            this.wideNodeLong = new long[totalSections];
            this.localNodeLong = new long[totalSections];
            this.incomingWide = new byte[totalSections];
            this.processedWide = new byte[totalSections];
            this.incomingLocal = new byte[totalSections];
            this.processedLocal = new byte[totalSections];
            this.cachedMaskUnion = new long[totalSections];
            this.wideEmittedIndices = new int[totalSections];
            this.wideEmittedEpoch = new int[totalSections];
            this.localEmittedIndices = new int[totalSections];
            this.localEmittedEpoch = new int[totalSections];
            this.emptyCacheEpoch = new int[totalSections];
            this.emptyCacheNode = new long[totalSections];
            this.emptyCacheFlag = new boolean[totalSections];
        }
        if (this.colCacheEpoch.length < gridCols) {
            this.colCacheEpoch = new int[gridCols];
            this.colCacheChunkPos = new long[gridCols];
            this.colCacheLoaded = new boolean[gridCols];
        }
        int wordCount = (totalSections + 63) >>> 6;
        if (this.localPortalBitset.length < wordCount) {
            this.widePortalBitset = new long[wordCount];
            this.localPortalBitset = new long[wordCount];
        }
    }

    private void advanceGridStateEpoch(int totalSections, int gridCols) {
        this.gridStateEpoch++;
        if (this.gridStateEpoch == Integer.MAX_VALUE) {
            Arrays.fill(this.emptyCacheEpoch, 0, Math.min(totalSections, this.emptyCacheEpoch.length), 0);
            Arrays.fill(this.colCacheEpoch, 0, Math.min(gridCols, this.colCacheEpoch.length), 0);
            this.gridStateEpoch = 1;
        }
    }

    private boolean isEmptySectionCached(int idx, long secNode) {
        if (this.emptyCacheEpoch[idx] == this.gridStateEpoch && this.emptyCacheNode[idx] == secNode) {
            return this.emptyCacheFlag[idx];
        }
        boolean empty = this.workerEmptySections.contains(secNode);
        this.emptyCacheEpoch[idx] = this.gridStateEpoch;
        this.emptyCacheNode[idx] = secNode;
        this.emptyCacheFlag[idx] = empty;
        return empty;
    }

    private void markWidePortal(int index) {
        this.widePortalBitset[index >>> 6] |= (1L << index);
    }

    private boolean isLocalPortal(int index) {
        return (this.localPortalBitset[index >>> 6] & (1L << index)) != 0L;
    }

    private void markLocalPortal(int index) {
        this.localPortalBitset[index >>> 6] |= (1L << index);
    }

    private boolean isFullAborted(OcclusionRequest req) {
        return this.worldGeneration.get() != req.generation() || this.fullRequestSeq.get() > req.seq();
    }

    private void processRequest(OcclusionRequest req) {
        GridLayout grid = req.grid();
        int totalSections = grid.sections().length;
        int gridCols = grid.gridSizeXZ() * grid.gridSizeXZ();

        if (totalSections == 0) {
            this.workerHasValidWideGraph = false;
            this.workerHasValidLocalGraph = false;
            return;
        }

        this.ensureWorkerCapacity(totalSections, gridCols);

        if (this.workerGraphCenterSecX != req.centerSecX() || this.workerGraphCenterSecZ != req.centerSecZ()) {
            this.advanceGridStateEpoch(totalSections, gridCols);
        }

        boolean sameGraphContext = this.workerGraphGeneration == req.generation()
                && this.workerGraphCamSecX == req.camSecX()
                && this.workerGraphCamSecY == req.camSecY()
                && this.workerGraphCamSecZ == req.camSecZ()
                && this.workerGraphCenterSecX == req.centerSecX()
                && this.workerGraphCenterSecZ == req.centerSecZ()
                && this.workerGraphSmartCull == req.smartCull();

        boolean wideGraphValid = !req.forceWideFull() && this.workerHasValidWideGraph && sameGraphContext;
        boolean localGraphValid = wideGraphValid && !req.forceLocalFull() && this.workerHasValidLocalGraph;

        this.deltaWokenIndices.clear();
        boolean deltaForcesFull = this.drainChunkDeltas(
                req.generation(),
                grid,
                req.centerSecX(),
                req.centerSecZ(),
                wideGraphValid,
                this.deltaWokenIndices
        );

        if (this.worldGeneration.get() != req.generation()) {
            this.workerHasValidWideGraph = false;
            this.workerHasValidLocalGraph = false;
            return;
        }

        if (deltaForcesFull || !this.workerHasValidWideGraph) {
            wideGraphValid = false;
            localGraphValid = false;
        }

        if (wideGraphValid && this.hasStrictPropagation(req.generation(), grid)) {
            wideGraphValid = false;
            localGraphValid = false;
        }

        if (!wideGraphValid) {
            this.workerHasValidWideGraph = false;
            this.workerHasValidLocalGraph = false;
            this.advanceWideEpoch(totalSections);
            this.advanceLocalEpoch(totalSections);
            this.workerWaitingByChunk.clear();
            this.workerWaitingChunks.clear();
            this.propagationQueue.clear();
            this.loadedExpectedQueue.clear();
            this.wideEmittedCount = 0;
            this.localEmittedCount = 0;

            int writeCount = this.seedFullBfs(req, grid, true, true);
            if (this.runLayeredBfsAborted(req, grid, true, true, writeCount, false)) {
                this.workerHasValidWideGraph = false;
                this.workerHasValidLocalGraph = false;
                return;
            }
            this.workerHasValidWideGraph = true;
            this.workerHasValidLocalGraph = true;
            this.recordGraphContext(req);
            this.publishSnapshots(req, grid, true, true);
            return;
        }

        int prevWideCount = this.wideEmittedCount;
        int prevLocalCount = this.localEmittedCount;

        this.advanceQueueToken(totalSections);
        int incWriteCount = 0;
        for (int i = 0, n = this.deltaWokenIndices.size(); i < n; i++) {
            if (this.enqueueToWrite(this.deltaWokenIndices.getInt(i), incWriteCount)) {
                incWriteCount++;
            }
        }
        incWriteCount = this.seedIncrementalBfs(req.generation(), grid, incWriteCount);
        boolean hasIncrementalWork = incWriteCount > 0;

        if (!hasIncrementalWork && !req.forceLocalFull() && this.workerHasValidLocalGraph) {
            return;
        }

        boolean publishWide = false;
        boolean publishLocal = false;

        if (hasIncrementalWork) {
            boolean runLocalInc = localGraphValid && !req.forceLocalFull();
            if (this.runLayeredBfsAborted(req, grid, true, runLocalInc, incWriteCount, true)) {
                this.workerHasValidWideGraph = false;
                this.workerHasValidLocalGraph = false;
                return;
            }
            publishWide = true;
            if (runLocalInc && this.localEmittedCount != prevLocalCount) {
                publishLocal = true;
            }
        }

        if (req.forceLocalFull() || !this.workerHasValidLocalGraph) {
            this.workerHasValidLocalGraph = false;
            this.advanceLocalEpoch(totalSections);
            this.localEmittedCount = 0;
            int localWriteCount = this.seedFullBfs(req, grid, false, true);
            if (this.runLayeredBfsAborted(req, grid, false, true, localWriteCount, false)) {
                this.workerHasValidLocalGraph = false;
                return;
            }
            this.workerHasValidLocalGraph = true;
            publishLocal = true;
        }

        this.recordGraphContext(req);

        if (publishWide || publishLocal || this.wideEmittedCount != prevWideCount) {
            this.publishSnapshots(req, grid, publishWide, publishLocal);
        }
    }

    private void recordGraphContext(OcclusionRequest req) {
        this.workerGraphGeneration = req.generation();
        this.workerGraphCamSecX = req.camSecX();
        this.workerGraphCamSecY = req.camSecY();
        this.workerGraphCamSecZ = req.camSecZ();
        this.workerGraphCenterSecX = req.centerSecX();
        this.workerGraphCenterSecZ = req.centerSecZ();
        this.workerGraphSmartCull = req.smartCull();
    }

    private void advanceWideEpoch(int totalSections) {
        this.wideEpoch++;
        if (this.wideEpoch == Integer.MAX_VALUE) {
            Arrays.fill(this.nodeWideEpoch, 0, totalSections, 0);
            Arrays.fill(this.wideEmittedEpoch, 0, totalSections, 0);
            this.wideEpoch = 1;
        }
        int wordCount = (totalSections + 63) >>> 6;
        Arrays.fill(this.widePortalBitset, 0, wordCount, 0L);
        this.advanceQueueToken(totalSections);
    }

    private void advanceLocalEpoch(int totalSections) {
        this.localEpoch++;
        if (this.localEpoch == Integer.MAX_VALUE) {
            Arrays.fill(this.nodeLocalEpoch, 0, totalSections, 0);
            Arrays.fill(this.localEmittedEpoch, 0, totalSections, 0);
            this.localEpoch = 1;
        }
        int wordCount = (totalSections + 63) >>> 6;
        Arrays.fill(this.localPortalBitset, 0, wordCount, 0L);
        this.advanceQueueToken(totalSections);
    }

    private void advanceQueueToken(int totalSections) {
        this.queueToken++;
        if (this.queueToken == Integer.MAX_VALUE) {
            Arrays.fill(this.queuedToken, 0, totalSections, 0);
            this.queueToken = 1;
        }
    }

    private boolean enqueueToWrite(int idx, int writeCount) {
        if (this.queuedToken[idx] == this.queueToken) {
            return false;
        }
        this.queuedToken[idx] = this.queueToken;
        this.writeQueue[writeCount] = idx;
        return true;
    }

    private boolean hasStrictPropagation(long gen, GridLayout grid) {
        RenderSection[] sections = grid.sections();
        PropagationEvent ev;
        while ((ev = this.propagationQueue.poll()) != null) {
            if (ev.generation() != gen) {
                continue;
            }
            RenderSection compiled = ev.section();
            int idx = compiled.index;
            if (idx < 0 || idx >= sections.length || sections[idx] != compiled) {
                continue;
            }
            if (this.nodeWideEpoch[idx] != this.wideEpoch || this.wideNodeLong[idx] != compiled.getSectionNode()) {
                continue;
            }
            long newUnion = this.computeMaskUnion(compiled);
            if ((this.cachedMaskUnion[idx] & ~newUnion) != 0L) {
                return true;
            }
            this.cachedMaskUnion[idx] = newUnion;
            this.processedWide[idx] = 0;
            if (this.nodeLocalEpoch[idx] == this.localEpoch && this.localNodeLong[idx] == compiled.getSectionNode()) {
                this.processedLocal[idx] = 0;
            }
            this.deltaWokenIndices.add(idx);
        }
        return false;
    }

    private long computeMaskUnion(RenderSection section) {
        SectionMesh mesh = section.getSectionMesh();
        if (mesh == CompiledSectionMesh.EMPTY) {
            return FULL_VISIBILITY_MASK;
        }
        if (mesh == CompiledSectionMesh.UNCOMPILED) {
            return 0L;
        }
        if (mesh instanceof XenoCompiledSectionMeshExtension ext) {
            long[] masks = ext.xeno$getDirectionalMasks();
            if (masks != null) {
                long union = 0L;
                for (int i = 0; i < masks.length; i++) {
                    union |= masks[i];
                }
                return union;
            }
            return ext.xeno$getVisibilityMask();
        }
        return 0L;
    }

    private int seedIncrementalBfs(long expectedGen, GridLayout grid, int writeCount) {
        int centerSecX = this.workerGraphCenterSecX;
        int centerSecZ = this.workerGraphCenterSecZ;

        LoadedExpectedEvent loadedEvent;
        while ((loadedEvent = this.loadedExpectedQueue.poll()) != null) {
            if (loadedEvent.generation() != expectedGen) {
                continue;
            }
            for (long chunkPos : loadedEvent.chunks()) {
                this.workerWaitingChunks.remove(chunkPos);
                LongArrayList waitingSections = this.workerWaitingByChunk.remove(chunkPos);
                if (waitingSections == null) {
                    continue;
                }
                for (int i = 0, n = waitingSections.size(); i < n; i++) {
                    long sNode = waitingSections.getLong(i);
                    int idx = grid.indexFor(
                            SectionPos.x(sNode),
                            SectionPos.y(sNode),
                            SectionPos.z(sNode),
                            centerSecX,
                            centerSecZ
                    );
                    if (idx >= 0 && this.nodeWideEpoch[idx] == this.wideEpoch && this.wideNodeLong[idx] == sNode) {
                        if (this.enqueueToWrite(idx, writeCount)) {
                            writeCount++;
                        }
                    }
                }
            }
        }
        return writeCount;
    }

    private int seedFullBfs(OcclusionRequest req, GridLayout grid, boolean seedWide, boolean seedLocal) {
        int camSecX = req.camSecX();
        int camSecY = req.camSecY();
        int camSecZ = req.camSecZ();
        int centerSecX = req.centerSecX();
        int centerSecZ = req.centerSecZ();
        RenderSection[] gridSections = grid.sections();
        int writeCount = 0;

        if (camSecY >= grid.minSectionY() && camSecY <= grid.maxSectionY()) {
            int startIdx = grid.indexFor(camSecX, camSecY, camSecZ, centerSecX, centerSecZ);
            if (startIdx >= 0) {
                RenderSection startSection = gridSections[startIdx];
                long startNode = SectionPos.asLong(camSecX, camSecY, camSecZ);
                if (startSection != null && startSection.getSectionNode() == startNode) {
                    this.initSeedNode(startIdx, startNode, seedWide, seedLocal);
                    if (this.enqueueToWrite(startIdx, writeCount)) {
                        writeCount++;
                    }
                    return writeCount;
                }
            }

            int clampedX = Math.max(centerSecX - grid.viewDistance(), Math.min(centerSecX + grid.viewDistance(), camSecX));
            int clampedZ = Math.max(centerSecZ - grid.viewDistance(), Math.min(centerSecZ + grid.viewDistance(), camSecZ));
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int sx = clampedX + dx;
                    int sz = clampedZ + dz;
                    int idx = grid.indexFor(sx, camSecY, sz, centerSecX, centerSecZ);
                    if (idx < 0) {
                        continue;
                    }
                    RenderSection sec = gridSections[idx];
                    long sNode = SectionPos.asLong(sx, camSecY, sz);
                    if (sec != null && sec.getSectionNode() == sNode) {
                        this.initSeedNode(idx, sNode, seedWide, seedLocal);
                        if (this.enqueueToWrite(idx, writeCount)) {
                            writeCount++;
                        }
                    }
                }
            }
            return writeCount;
        }

        boolean below = camSecY < grid.minSectionY();
        int seedY = below ? grid.minSectionY() : grid.maxSectionY();
        int vd = grid.viewDistance();

        for (int radius = 0; radius <= vd * 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int absDx = Math.abs(dx);
                if (absDx > vd) {
                    continue;
                }
                int absDz = radius - absDx;
                if (absDz > vd) {
                    continue;
                }
                for (int sign = 0; sign < 2; sign++) {
                    if (absDz == 0 && sign == 1) {
                        continue;
                    }
                    int dz = sign == 0 ? absDz : -absDz;
                    int sx = centerSecX + dx;
                    int sz = centerSecZ + dz;
                    if (!ChunkTrackingView.isInViewDistance(centerSecX, centerSecZ, vd, sx, sz)) {
                        continue;
                    }
                    int idx = grid.indexFor(sx, seedY, sz, centerSecX, centerSecZ);
                    if (idx < 0) {
                        continue;
                    }
                    RenderSection sec = gridSections[idx];
                    long sNode = SectionPos.asLong(sx, seedY, sz);
                    if (sec != null && sec.getSectionNode() == sNode) {
                        this.initSeedNode(idx, sNode, seedWide, seedLocal);
                        if (this.enqueueToWrite(idx, writeCount)) {
                            writeCount++;
                        }
                    }
                }
            }
        }
        return writeCount;
    }

    private void initSeedNode(int idx, long sNode, boolean seedWide, boolean seedLocal) {
        if (seedWide) {
            this.nodeWideEpoch[idx] = this.wideEpoch;
            this.wideNodeLong[idx] = sNode;
            this.incomingWide[idx] = (byte) 0x40;
            this.processedWide[idx] = 0;
            this.markWidePortal(idx);
        }
        if (seedLocal) {
            this.nodeLocalEpoch[idx] = this.localEpoch;
            this.localNodeLong[idx] = sNode;
            this.incomingLocal[idx] = (byte) 0x40;
            this.processedLocal[idx] = 0;
            this.markLocalPortal(idx);
        }
    }

    private boolean runLayeredBfsAborted(
            OcclusionRequest req,
            GridLayout grid,
            boolean runWide,
            boolean runLocal,
            int writeCount,
            boolean incremental
    ) {
        int camSecX = req.camSecX();
        int camSecY = req.camSecY();
        int camSecZ = req.camSecZ();
        int centerSecX = req.centerSecX();
        int centerSecZ = req.centerSecZ();
        double camX = req.camX();
        double camY = req.camY();
        double camZ = req.camZ();
        boolean smartCull = req.smartCull();
        XenoBranchlessFrustum localFrustum = req.localFrustum();
        float bfCamX = localFrustum != null ? (float) localFrustum.getCamX() : 0.0f;
        float bfCamY = localFrustum != null ? (float) localFrustum.getCamY() : 0.0f;
        float bfCamZ = localFrustum != null ? (float) localFrustum.getCamZ() : 0.0f;

        RenderSection[] gridSections = grid.sections();
        int viewDistance = grid.viewDistance();
        int minSectionY = grid.minSectionY();
        int maxSectionY = grid.maxSectionY();
        int gridSizeY = grid.gridSizeY();
        int gridSizeXZ = grid.gridSizeXZ();
        int strideZ = gridSizeY * gridSizeXZ;
        int minGridX = centerSecX - viewDistance;
        int maxGridX = centerSecX + viewDistance;
        int minGridZ = centerSecZ - viewDistance;
        int maxGridZ = centerSecZ + viewDistance;
        int wEpoch = this.wideEpoch;
        int lEpoch = this.localEpoch;
        int totalSections = gridSections.length;

        int iterations = 0;
        while (writeCount > 0) {
            if (this.isFullAborted(req)) {
                return true;
            }

            int[] temp = this.readQueue;
            this.readQueue = this.writeQueue;
            this.writeQueue = temp;
            int readCount = writeCount;
            writeCount = 0;
            this.advanceQueueToken(totalSections);

            for (int r = 0; r < readCount; r++) {
                if ((++iterations & 63) == 0 && this.isFullAborted(req)) {
                    return true;
                }

                int curIdx = this.readQueue[r];
                RenderSection section = gridSections[curIdx];
                if (section == null) {
                    continue;
                }
                long secNode = runWide ? this.wideNodeLong[curIdx] : this.localNodeLong[curIdx];
                if (section.getSectionNode() != secNode) {
                    if (this.isFullAborted(req)) {
                        return true;
                    }
                    continue;
                }

                int curModZ = curIdx / strideZ;
                int rem = curIdx - curModZ * strideZ;
                int curYOff = rem / gridSizeXZ;
                int curModX = rem - curYOff * gridSizeXZ;

                int secX = SectionPos.x(secNode);
                int secZ = SectionPos.z(secNode);
                long chunkPos = ChunkPos.fromSectionNode(secNode);
                int colSlot = curModZ * gridSizeXZ + curModX;
                boolean loaded;
                if (this.colCacheEpoch[colSlot] == this.gridStateEpoch && this.colCacheChunkPos[colSlot] == chunkPos) {
                    loaded = this.colCacheLoaded[colSlot];
                } else {
                    loaded = this.workerLoadedChunks.contains(chunkPos);
                    this.colCacheEpoch[colSlot] = this.gridStateEpoch;
                    this.colCacheChunkPos[colSlot] = chunkPos;
                    this.colCacheLoaded[colSlot] = loaded;
                }
                if (!loaded) {
                    if (runWide) {
                        this.recordWaitingChunk(chunkPos, secNode);
                    }
                    continue;
                }

                byte pendingWide = 0;
                if (runWide && this.nodeWideEpoch[curIdx] == wEpoch && this.wideNodeLong[curIdx] == secNode) {
                    byte inWide = this.incomingWide[curIdx];
                    pendingWide = (byte) (inWide & ~this.processedWide[curIdx]);
                    this.processedWide[curIdx] = inWide;
                }

                byte pendingLocal = 0;
                if (runLocal && this.nodeLocalEpoch[curIdx] == lEpoch && this.localNodeLong[curIdx] == secNode) {
                    byte inLocal = this.incomingLocal[curIdx];
                    pendingLocal = (byte) (inLocal & ~this.processedLocal[curIdx]);
                    this.processedLocal[curIdx] = inLocal;
                }

                if (pendingWide == 0 && pendingLocal == 0) {
                    continue;
                }

                int secY = SectionPos.y(secNode);
                boolean nearCam = Math.abs(secX - camSecX) <= 1
                        && Math.abs(secY - camSecY) <= 1
                        && Math.abs(secZ - camSecZ) <= 1;

                boolean isEmptyChunkSection = this.isEmptySectionCached(curIdx, secNode);

                if (pendingWide != 0) {
                    this.markWidePortal(curIdx);
                }

                boolean localVisible = false;
                if (pendingLocal != 0) {
                    localVisible = true;
                    if (smartCull && !nearCam && !isEmptyChunkSection && this.isRayBlockedStepped(secX, secY, secZ, camX, camY, camZ, minSectionY, maxSectionY, centerSecX, centerSecZ, grid, lEpoch)) {
                        localVisible = false;
                    }
                    if (localVisible) {
                        this.markLocalPortal(curIdx);
                    }
                }

                if (!isEmptyChunkSection) {
                    if (pendingWide != 0 && this.wideEmittedEpoch[curIdx] != wEpoch) {
                        this.wideEmittedEpoch[curIdx] = wEpoch;
                        this.wideEmittedIndices[this.wideEmittedCount++] = curIdx;
                    }
                    if (localVisible && this.localEmittedEpoch[curIdx] != lEpoch) {
                        this.localEmittedEpoch[curIdx] = lEpoch;
                        this.localEmittedIndices[this.localEmittedCount++] = curIdx;
                    }
                }
                SectionMesh mesh = section.getSectionMesh();
                boolean isEmpty = isEmptyChunkSection || mesh == CompiledSectionMesh.EMPTY;
                boolean isUncompiled = !isEmpty && mesh == CompiledSectionMesh.UNCOMPILED;
                long[] dirMasks = null;
                long fallbackOrSingleMask = 0L;
                long maskUnion;
                if (isEmpty) {
                    maskUnion = FULL_VISIBILITY_MASK;
                    fallbackOrSingleMask = FULL_VISIBILITY_MASK;
                } else if (isUncompiled) {
                    maskUnion = 0L;
                } else if (mesh instanceof XenoCompiledSectionMeshExtension ext) {
                    dirMasks = ext.xeno$getDirectionalMasks();
                    if (dirMasks != null) {
                        if (dirMasks.length == 1) {
                            maskUnion = dirMasks[0];
                            fallbackOrSingleMask = dirMasks[0];
                            dirMasks = null;
                        } else {
                            maskUnion = dirMasks[0] | dirMasks[1] | dirMasks[2] | dirMasks[3];
                        }
                    } else {
                        maskUnion = ext.xeno$getVisibilityMask();
                        fallbackOrSingleMask = maskUnion;
                    }
                } else {
                    maskUnion = FULL_VISIBILITY_MASK;
                    fallbackOrSingleMask = FULL_VISIBILITY_MASK;
                }
                this.cachedMaskUnion[curIdx] = maskUnion;

                double absDx = 0.0;
                double absDy = 0.0;
                double absDz = 0.0;
                if (smartCull && (pendingWide != 0 || localVisible)) {
                    absDx = Math.abs(camX - ((secX << 4) + 8.0));
                    absDy = Math.abs(camY - ((secY << 4) + 8.0));
                    absDz = Math.abs(camZ - ((secZ << 4) + 8.0));
                }

                int outgoingWide = 0;
                if (pendingWide != 0) {
                    outgoingWide = !smartCull
                            ? 0x3F
                            : this.computeOutgoingMask(isUncompiled, isEmpty, dirMasks, fallbackOrSingleMask, pendingWide, secX, secY, secZ, camSecX, camSecY, camSecZ, camX, camY, camZ, absDx, absDy, absDz, true);
                    outgoingWide &= getOutwardDirectionsWide(secX, secY, secZ, camSecX, camSecY, camSecZ);
                }

                int outgoingLocal = 0;
                if (localVisible) {
                    outgoingLocal = !smartCull
                            ? 0x3F
                            : this.computeOutgoingMask(isUncompiled, isEmpty, dirMasks, fallbackOrSingleMask, pendingLocal, secX, secY, secZ, camSecX, camSecY, camSecZ, camX, camY, camZ, absDx, absDy, absDz, false);
                    outgoingLocal &= getOutwardDirectionsLocal(secX, secY, secZ, camSecX, camSecY, camSecZ);
                }

                if (outgoingWide == 0 && outgoingLocal == 0) {
                    continue;
                }

                for (int dirIdx = 0; dirIdx < 6; dirIdx++) {
                    int bit = 1 << dirIdx;
                    boolean goWide = (outgoingWide & bit) != 0;
                    boolean goLocal = (outgoingLocal & bit) != 0;
                    if (!goWide && !goLocal) {
                        continue;
                    }

                    int stepX = DIR_DX[dirIdx];
                    int stepY = DIR_DY[dirIdx];
                    int stepZ = DIR_DZ[dirIdx];
                    int nx = secX + stepX;
                    int ny = secY + stepY;
                    int nz = secZ + stepZ;

                    if (ny < minSectionY || ny > maxSectionY || nx < minGridX || nx > maxGridX || nz < minGridZ || nz > maxGridZ) {
                        continue;
                    }
                    if (!ChunkTrackingView.isInViewDistance(centerSecX, centerSecZ, viewDistance, nx, nz)) {
                        continue;
                    }

                    int nModX = curModX + stepX;
                    if (nModX < 0) nModX += gridSizeXZ;
                    else if (nModX >= gridSizeXZ) nModX -= gridSizeXZ;

                    int nModZ = curModZ + stepZ;
                    if (nModZ < 0) nModZ += gridSizeXZ;
                    else if (nModZ >= gridSizeXZ) nModZ -= gridSizeXZ;

                    int nIdx = nModZ * strideZ + (curYOff + stepY) * gridSizeXZ + nModX;
                    RenderSection neighbor = gridSections[nIdx];
                    long nNode = SectionPos.asLong(nx, ny, nz);
                    if (neighbor == null || neighbor.getSectionNode() != nNode) {
                        continue;
                    }

                    if (goLocal && localFrustum != null) {
                        boolean neighborNearCam = Math.abs(nx - camSecX) <= 1
                                && Math.abs(ny - camSecY) <= 1
                                && Math.abs(nz - camSecZ) <= 1;
                        if (!neighborNearCam) {
                            float ncx = (nx << 4) + 8.0f - bfCamX;
                            float ncy = (ny << 4) + 8.0f - bfCamY;
                            float ncz = (nz << 4) + 8.0f - bfCamZ;
                            if (localFrustum.isPaddedSectionCulledRel(ncx, ncy, ncz)) {
                                goLocal = false;
                            }
                        }
                    }

                    boolean stateAdded = false;
                    if (goWide) {
                        if (this.nodeWideEpoch[nIdx] != wEpoch || this.wideNodeLong[nIdx] != nNode) {
                            this.nodeWideEpoch[nIdx] = wEpoch;
                            this.wideNodeLong[nIdx] = nNode;
                            this.incomingWide[nIdx] = 0;
                            this.processedWide[nIdx] = 0;
                        }
                        byte oldW = this.incomingWide[nIdx];
                        byte newW = (byte) (oldW | bit);
                        if (newW != oldW) {
                            this.incomingWide[nIdx] = newW;
                            if ((newW & ~this.processedWide[nIdx]) != 0) {
                                stateAdded = true;
                            }
                        }
                    }
                    if (goLocal) {
                        if (this.nodeLocalEpoch[nIdx] != lEpoch || this.localNodeLong[nIdx] != nNode) {
                            this.nodeLocalEpoch[nIdx] = lEpoch;
                            this.localNodeLong[nIdx] = nNode;
                            this.incomingLocal[nIdx] = 0;
                            this.processedLocal[nIdx] = 0;
                        }
                        byte oldL = this.incomingLocal[nIdx];
                        byte newL = (byte) (oldL | bit);
                        if (newL != oldL) {
                            this.incomingLocal[nIdx] = newL;
                            if ((newL & ~this.processedLocal[nIdx]) != 0) {
                                stateAdded = true;
                            }
                        }
                    }

                    if (stateAdded && this.enqueueToWrite(nIdx, writeCount)) {
                        writeCount++;
                    }
                }
            }

            if (incremental) {
                this.drainAdditionsMidPass(req.generation(), grid);
            }
        }

        if (this.isFullAborted(req)) {
            return true;
        }

        if (!incremental && runLocal && localFrustum != null) {
            this.addNearbyLocalSections(camSecX, camSecY, camSecZ, centerSecX, centerSecZ, bfCamX, bfCamY, bfCamZ, localFrustum, grid, lEpoch);
        }

        return false;
    }

    private void recordWaitingChunk(long chunkPos, long secNode) {
        this.workerWaitingChunks.add(chunkPos);
        LongArrayList list = this.workerWaitingByChunk.get(chunkPos);
        if (list == null) {
            list = new LongArrayList(4);
            list.add(secNode);
            this.workerWaitingByChunk.put(chunkPos, list);
        } else if (!list.contains(secNode)) {
            list.add(secNode);
        }
    }

    private void drainAdditionsMidPass(long gen, GridLayout grid) {
        RenderSection[] sections = grid.sections();
        PropagationEvent ev;
        while ((ev = this.propagationQueue.poll()) != null) {
            if (ev.generation() != gen) {
                continue;
            }
            RenderSection compiled = ev.section();
            int idx = compiled.index;
            if (idx >= 0 && idx < sections.length && sections[idx] == compiled
                    && this.nodeWideEpoch[idx] == this.wideEpoch
                    && this.wideNodeLong[idx] == compiled.getSectionNode()) {
                this.cachedMaskUnion[idx] = this.computeMaskUnion(compiled);
            }
        }
    }

    private void addNearbyLocalSections(
            int camSecX,
            int camSecY,
            int camSecZ,
            int centerSecX,
            int centerSecZ,
            float bfCamX,
            float bfCamY,
            float bfCamZ,
            XenoBranchlessFrustum localFrustum,
            GridLayout grid,
            int lEpoch
    ) {
        RenderSection[] sections = grid.sections();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int sx = camSecX + dx;
                    int sy = camSecY + dy;
                    int sz = camSecZ + dz;
                    int idx = grid.indexFor(sx, sy, sz, centerSecX, centerSecZ);
                    if (idx < 0) {
                        continue;
                    }
                    RenderSection sec = sections[idx];
                    long sNode = SectionPos.asLong(sx, sy, sz);
                    if (sec == null || sec.getSectionNode() != sNode) {
                        continue;
                    }
                    if (localFrustum.isPaddedSectionCulledRel((sx << 4) + 8.0f - bfCamX, (sy << 4) + 8.0f - bfCamY, (sz << 4) + 8.0f - bfCamZ)) {
                        continue;
                    }
                    this.nodeLocalEpoch[idx] = lEpoch;
                    this.localNodeLong[idx] = sNode;
                    this.markLocalPortal(idx);
                    if (!this.isEmptySectionCached(idx, sNode) && this.localEmittedEpoch[idx] != lEpoch) {
                        this.localEmittedEpoch[idx] = lEpoch;
                        this.localEmittedIndices[this.localEmittedCount++] = idx;
                    }
                }
            }
        }
    }

    private int computeOutgoingMask(
            boolean isUncompiled,
            boolean emptyChunkSection,
            long @Nullable [] dirMasks,
            long fallbackOrSingleMask,
            byte pendingInDirs,
            int secX,
            int secY,
            int secZ,
            int camSecX,
            int camSecY,
            int camSecZ,
            double camX,
            double camY,
            double camZ,
            double absDx,
            double absDy,
            double absDz,
            boolean wide
    ) {
        if ((pendingInDirs & 0x40) != 0) {
            if (emptyChunkSection) {
                return 0x3F;
            }
            if (isUncompiled) {
                return 0;
            }
            long mask = dirMasks != null
                    ? this.combineOctantMasks(dirMasks, secX, secY, secZ, camSecX, camSecY, camSecZ, camX, camY, camZ, wide)
                    : fallbackOrSingleMask;
            int exits = 0;
            for (int exit = 0; exit < 6; exit++) {
                if (((mask >>> (exit * 6)) & 0x3FL) != 0L) {
                    exits |= 1 << exit;
                }
            }
            return exits;
        }

        if (isUncompiled) {
            return 0;
        }

        long visMask = emptyChunkSection
                ? FULL_VISIBILITY_MASK
                : (dirMasks != null
                ? this.combineOctantMasks(dirMasks, secX, secY, secZ, camSecX, camSecY, camSecZ, camX, camY, camZ, wide)
                : fallbackOrSingleMask);
        visMask &= computeAngleMask(absDx, absDy, absDz, wide);

        int inBits = pendingInDirs & 0x3F;
        int outgoing = 0;
        for (int exit = 0; exit < 6; exit++) {
            if (((visMask >>> (exit * 6)) & inBits) != 0L) {
                outgoing |= 1 << exit;
            }
        }
        return outgoing;
    }

    private long combineOctantMasks(
            long[] dirMasks,
            int secX,
            int secY,
            int secZ,
            int camSecX,
            int camSecY,
            int camSecZ,
            double camX,
            double camY,
            double camZ,
            boolean wide
    ) {
        int sx = 0;
        int sz = 0;
        int sy = 0;
        if (wide) {
            if (secX >= camSecX - 1) sx |= 0b11110000;
            if (secX <= camSecX + 1) sx |= 0b00001111;
            if (secZ >= camSecZ - 1) sz |= 0b11001100;
            if (secZ <= camSecZ + 1) sz |= 0b00110011;
            if (secY >= camSecY - 1) sy |= 0b10101010;
            if (secY <= camSecY + 1) sy |= 0b01010101;
        } else {
            double ox = secX << 4;
            double oy = secY << 4;
            double oz = secZ << 4;
            if (ox + 16.0 >= camX) sx |= 0b11110000;
            if (ox <= camX) sx |= 0b00001111;
            if (oz + 16.0 >= camZ) sz |= 0b11001100;
            if (oz <= camZ) sz |= 0b00110011;
            if (oy + 16.0 >= camY) sy |= 0b10101010;
            if (oy <= camY) sy |= 0b01010101;
        }
        int octMask = sx & sz & sy;
        long combined = 0L;
        if ((octMask & 0b10000001) != 0) combined |= dirMasks[0];
        if ((octMask & 0b01000010) != 0) combined |= dirMasks[1];
        if ((octMask & 0b00100100) != 0) combined |= dirMasks[2];
        if ((octMask & 0b00011000) != 0) combined |= dirMasks[3];
        return combined;
    }

    private static long computeAngleMask(double dx, double dy, double dz, boolean wide) {
        long occluded = 0L;
        if (wide) {
            if (dx > dy + WIDE_ANGLE_MARGIN || dz > dy + WIDE_ANGLE_MARGIN) {
                occluded |= UP_DOWN_MASK;
            }
            if (dx > dz + WIDE_ANGLE_MARGIN || dy > dz + WIDE_ANGLE_MARGIN) {
                occluded |= NORTH_SOUTH_MASK;
            }
            if (dy > dx + WIDE_ANGLE_MARGIN || dz > dx + WIDE_ANGLE_MARGIN) {
                occluded |= WEST_EAST_MASK;
            }
        } else {
            if (dx > dy || dz > dy) {
                occluded |= UP_DOWN_MASK;
            }
            if (dx > dz || dy > dz) {
                occluded |= NORTH_SOUTH_MASK;
            }
            if (dy > dx || dz > dx) {
                occluded |= WEST_EAST_MASK;
            }
        }
        return ~occluded;
    }

    private static int getOutwardDirectionsLocal(int secX, int secY, int secZ, int camSecX, int camSecY, int camSecZ) {
        int dirs = 0;
        if (secX <= camSecX) dirs |= 1 << Direction.WEST.ordinal();
        if (secX >= camSecX) dirs |= 1 << Direction.EAST.ordinal();
        if (secY <= camSecY) dirs |= 1 << Direction.DOWN.ordinal();
        if (secY >= camSecY) dirs |= 1 << Direction.UP.ordinal();
        if (secZ <= camSecZ) dirs |= 1 << Direction.NORTH.ordinal();
        if (secZ >= camSecZ) dirs |= 1 << Direction.SOUTH.ordinal();
        return dirs;
    }

    private static int getOutwardDirectionsWide(int secX, int secY, int secZ, int camSecX, int camSecY, int camSecZ) {
        int dirs = 0;
        if (secX <= camSecX + 1) dirs |= 1 << Direction.WEST.ordinal();
        if (secX >= camSecX - 1) dirs |= 1 << Direction.EAST.ordinal();
        if (secY <= camSecY + 1) dirs |= 1 << Direction.DOWN.ordinal();
        if (secY >= camSecY - 1) dirs |= 1 << Direction.UP.ordinal();
        if (secZ <= camSecZ + 1) dirs |= 1 << Direction.NORTH.ordinal();
        if (secZ >= camSecZ - 1) dirs |= 1 << Direction.SOUTH.ordinal();
        return dirs;
    }

    private boolean isRayBlockedStepped(
            int secX,
            int secY,
            int secZ,
            double camX,
            double camY,
            double camZ,
            int minSectionY,
            int maxSectionY,
            int centerSecX,
            int centerSecZ,
            GridLayout grid,
            int lEpoch
    ) {
        double x = (secX << 4) + 8.0;
        double y = (secY << 4) + 8.0;
        double z = (secZ << 4) + 8.0;
        double dx = camX - x;
        double dy = camY - y;
        double dz = camZ - z;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq <= RAY_MIN_DIST_SQ) {
            return false;
        }

        double length = Math.sqrt(distSq);
        int steps = Math.min((int) (length / RAY_STEP_DIVISOR), RAY_TEST_MAX_STEPS);
        if (steps <= 1) {
            return false;
        }

        double invSteps = 1.0 / steps;
        double stepX = dx * invSteps;
        double stepY = dy * invSteps;
        double stepZ = dz * invSteps;

        for (int i = 1; i < steps; i++) {
            x += stepX;
            y += stepY;
            z += stepZ;

            int ry = Mth.floor(y) >> 4;
            if (ry < minSectionY || ry > maxSectionY) {
                break;
            }
            int rx = Mth.floor(x) >> 4;
            int rz = Mth.floor(z) >> 4;

            if (this.hasPortalAt(rx, ry, rz, centerSecX, centerSecZ, grid, lEpoch)) {
                continue;
            }

            double radius = SECTION_HALF_DIAGONAL * ((steps - i) * invSteps);
            boolean hasPath = false;
            for (int corner = 0; corner < 8; corner++) {
                double cx = x + ((corner & 1) == 0 ? -radius : radius);
                double cy = y + ((corner & 2) == 0 ? -radius : radius);
                double cz = z + ((corner & 4) == 0 ? -radius : radius);
                int cry = Mth.floor(cy) >> 4;
                if (cry < minSectionY || cry > maxSectionY) {
                    hasPath = true;
                    break;
                }
                int crx = Mth.floor(cx) >> 4;
                int crz = Mth.floor(cz) >> 4;
                if (this.hasPortalAt(crx, cry, crz, centerSecX, centerSecZ, grid, lEpoch)) {
                    hasPath = true;
                    break;
                }
            }
            if (!hasPath) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPortalAt(
            int secX,
            int secY,
            int secZ,
            int centerSecX,
            int centerSecZ,
            GridLayout grid,
            int lEpoch
    ) {
        int idx = grid.indexFor(secX, secY, secZ, centerSecX, centerSecZ);
        if (idx < 0 || this.nodeLocalEpoch[idx] != lEpoch) {
            return false;
        }
        return this.localNodeLong[idx] == SectionPos.asLong(secX, secY, secZ) && this.isLocalPortal(idx);
    }

    private XenoOctreeForest acquirePooledForest(@Nullable XenoOctreeForest[] pool, GridLayout grid, int centerSecX, int centerSecZ) {
        XenoOctreeForest reading = this.renderThreadReadingForest;
        CullSnapshot wide = this.activeWideSnapshot;
        CullSnapshot local = this.activeLocalSnapshot;
        XenoOctreeForest liveWide = wide != null ? wide.forest : null;
        XenoOctreeForest liveLocal = local != null ? local.forest : null;

        for (int i = 0; i < pool.length; i++) {
            XenoOctreeForest candidate = pool[i];
            if (candidate == null) {
                candidate = new XenoOctreeForest(grid, centerSecX, centerSecZ);
                pool[i] = candidate;
                return candidate;
            }
            if (candidate != reading && candidate != liveWide && candidate != liveLocal) {
                candidate.resetFor(grid, centerSecX, centerSecZ);
                return candidate;
            }
        }
        return new XenoOctreeForest(grid, centerSecX, centerSecZ);
    }

    private void publishSnapshots(OcclusionRequest req, GridLayout grid, boolean publishWide, boolean publishLocal) {
        int wordCount = (grid.sections().length + 63) >>> 6;
        LongCollection waitingCopy;
        if (publishWide) {
            waitingCopy = LongSets.unmodifiable(new LongOpenHashSet(this.workerWaitingChunks));
            XenoOctreeForest wideForest = this.acquirePooledForest(Objects.requireNonNull(this.wideForestPool), grid, req.centerSecX(), req.centerSecZ());
            for (int i = 0; i < this.wideEmittedCount; i++) {
                long sNode = this.wideNodeLong[this.wideEmittedIndices[i]];
                wideForest.add(SectionPos.x(sNode), SectionPos.y(sNode), SectionPos.z(sNode));
            }
            wideForest.prepare();
            wideForest.copyPortalBits(this.widePortalBitset, wordCount);
            if (!this.isFullAborted(req)) {
                this.activeWideSnapshot = new CullSnapshot(
                        req.generation(),
                        req.localCameraSeq(),
                        req.camSecX(),
                        req.camSecY(),
                        req.camSecZ(),
                        wideForest,
                        waitingCopy
                );
                this.needsFrustumUpdate.set(true);
            }
        } else {
            CullSnapshot existingWide = this.activeWideSnapshot;
            waitingCopy = existingWide != null && existingWide.generation == req.generation()
                    ? existingWide.waitingChunks
                    : LongSets.EMPTY_SET;
        }

        if (publishLocal && req.localCameraSeq() >= this.validLocalFromSeq.get()) {
            XenoOctreeForest localForest = this.acquirePooledForest(Objects.requireNonNull(this.localForestPool), grid, req.centerSecX(), req.centerSecZ());
            for (int i = 0; i < this.localEmittedCount; i++) {
                long sNode = this.localNodeLong[this.localEmittedIndices[i]];
                localForest.add(SectionPos.x(sNode), SectionPos.y(sNode), SectionPos.z(sNode));
            }
            localForest.prepare();
            localForest.copyPortalBits(this.localPortalBitset, wordCount);
            if (!this.isFullAborted(req) && req.localCameraSeq() >= this.validLocalFromSeq.get()) {
                this.activeLocalSnapshot = new CullSnapshot(
                        req.generation(),
                        req.localCameraSeq(),
                        req.camSecX(),
                        req.camSecY(),
                        req.camSecZ(),
                        localForest,
                        waitingCopy
                );
                this.needsFrustumUpdate.set(true);
            }
        }
    }
}
