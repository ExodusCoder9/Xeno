package com.xeno.client.renderer.entity;

import com.xeno.client.mixin.ViewAreaAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * Asynchronous Path-Traced Entity Culler.
 * Casts DDA voxel rays from the camera to entity bounding boxes on a background thread
 * to cull entities hidden behind opaque terrain walls with 0ms main-thread overhead.
 */
public class XenoEntityCuller implements IXenoEntityCuller {

    public record RayTask(int entityId, Vec3 camPos, AABB aabb) {
    }

    private final ConcurrentHashMap<Integer, Boolean> visibilityMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> occlusionFrames = new ConcurrentHashMap<>();
    private final Queue<RayTask> rayQueue = new ConcurrentLinkedQueue<>();

    private ClientLevel currentLevel;
    private final Thread cullThread;

    public XenoEntityCuller() {
        this.cullThread = new Thread(this::cullWorkerLoop, "Xeno-EntityCullThread");
        this.cullThread.setDaemon(true);
        this.cullThread.setPriority(Thread.MIN_PRIORITY + 1);
        this.cullThread.start();
    }

    private void cullWorkerLoop() {
        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                RayTask task = this.rayQueue.poll();
                if (task == null) {
                    LockSupport.park(this);
                    continue;
                }

                ClientLevel level = this.currentLevel;
                if (level == null) continue;

                int id = task.entityId;
                Vec3 camPos = task.camPos;
                AABB aabb = task.aabb;

                Vec3 center = aabb.getCenter();
                Vec3 top = new Vec3(center.x, aabb.maxY - 0.1, center.z);
                Vec3 bottom = new Vec3(center.x, aabb.minY + 0.1, center.z);

                boolean centerBlocked = isRayBlocked(level, camPos, center, mutPos);
                boolean topBlocked = isRayBlocked(level, camPos, top, mutPos);
                boolean bottomBlocked = isRayBlocked(level, camPos, bottom, mutPos);

                boolean fullyOccluded = centerBlocked && topBlocked && bottomBlocked;

                if (fullyOccluded) {
                    int frames = this.occlusionFrames.merge(id, 1, Integer::sum);
                    // Require 3 consecutive occluded frames before hiding entity to prevent flickering
                    if (frames >= 3) {
                        this.visibilityMap.put(id, false);
                    }
                } else {
                    this.occlusionFrames.put(id, 0);
                    this.visibilityMap.put(id, true); // Immediately visible
                }
            } catch (Exception e) {
                // Silently absorb level access race conditions during chunk updates
            }
        }
    }

    private static boolean isRayBlocked(ClientLevel level, Vec3 start, Vec3 end, BlockPos.MutableBlockPos mutPos) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq < 0.1) return false;
        double dist = Math.sqrt(distSq);

        double steps = Math.ceil(dist * 2.0); // Sample every 0.5 blocks
        double stepX = dx / steps;
        double stepY = dy / steps;
        double stepZ = dz / steps;

        double currX = start.x;
        double currY = start.y;
        double currZ = start.z;

        for (int i = 0; i < steps; i++) {
            currX += stepX;
            currY += stepY;
            currZ += stepZ;

            int bx = (int) Math.floor(currX);
            int by = (int) Math.floor(currY);
            int bz = (int) Math.floor(currZ);

            mutPos.set(bx, by, bz);

            try {
                if (!level.hasChunkAt(mutPos)) continue;
                BlockState state = level.getBlockState(mutPos);
                if (state.isSolidRender()) {
                    return true;
                }
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean isEntityVisible(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            ClientLevel level,
            LevelRenderer levelRenderer,
            Minecraft minecraft
    ) {
        if (level == null) return false;
        this.currentLevel = level;

        // Player mounts and passengers are always visible
        if (minecraft.player != null && (entity == minecraft.player || entity.hasIndirectPassenger(minecraft.player))) {
            return true;
        }

        // Standard frustum & dispatcher checks
        if (!levelRenderer.entityRenderDispatcher().shouldRender(entity, frustum, camX, camY, camZ)) {
            return false;
        }

        AABB aabb = entity.getBoundingBox();
        int minSecX = SectionPos.blockToSectionCoord(aabb.minX);
        int minSecY = SectionPos.blockToSectionCoord(aabb.minY);
        int minSecZ = SectionPos.blockToSectionCoord(aabb.minZ);
        int maxSecX = SectionPos.blockToSectionCoord(aabb.maxX);
        int maxSecY = SectionPos.blockToSectionCoord(aabb.maxY);
        int maxSecZ = SectionPos.blockToSectionCoord(aabb.maxZ);

        ViewArea area = levelRenderer.viewArea();
        if (area == null) return false;

        long now = Util.getMillis();
        boolean sectionVisible = false;

        for (int secY = minSecY; secY <= maxSecY; secY++) {
            if (level.isOutsideBuildHeight(SectionPos.sectionToBlockCoord(secY))) {
                sectionVisible = true;
                break;
            }
            for (int secX = minSecX; secX <= maxSecX; secX++) {
                for (int secZ = minSecZ; secZ <= maxSecZ; secZ++) {
                    long sectionNode = SectionPos.asLong(secX, secY, secZ);
                    SectionRenderDispatcher.RenderSection section =
                            ((ViewAreaAccessor) area).invokeGetRenderSection(sectionNode);
                    if (section != null
                            && section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED
                            && section.getVisibility(now) >= 0.3F) {
                        sectionVisible = true;
                        break;
                    }
                }
                if (sectionVisible) break;
            }
            if (sectionVisible) break;
        }

        if (!sectionVisible) {
            return false;
        }

        int id = entity.getId();
        Vec3 camPos = new Vec3(camX, camY, camZ);
        this.rayQueue.add(new RayTask(id, camPos, aabb));
        LockSupport.unpark(this.cullThread);

        Boolean cachedVisibility = this.visibilityMap.get(id);
        return cachedVisibility == null || cachedVisibility;
    }

    @Override
    public void tickFrame() {
        // Reserved for frame cleanup if needed
    }

    @Override
    public void reset() {
        this.visibilityMap.clear();
        this.occlusionFrames.clear();
        this.rayQueue.clear();
        LockSupport.unpark(this.cullThread);
    }
}
