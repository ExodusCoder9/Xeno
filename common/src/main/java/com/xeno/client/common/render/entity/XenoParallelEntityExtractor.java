/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.common.render.entity;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public final class XenoParallelEntityExtractor {
    public static boolean ENABLED = true;
    private static final int BATCH_SIZE = 24;
    private XenoParallelEntityExtractor() {
    }
    public static boolean extractEntities(
            Minecraft minecraft,
            ClientLevel level,
            LevelRenderer levelRenderer,
            Camera camera,
            Frustum frustum,
            DeltaTracker deltaTracker,
            LevelRenderState levelRenderState
    ) {
        if (!ENABLED || level == null || levelRenderer == null) {
            return false;
        }

        final double distanceScaling = minecraft.options.entityDistanceScaling().get();
        final double viewScale = Mth.clamp(minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5) * distanceScaling;
        Entity.setViewScale(viewScale);

        final long chunkSectionFadeInTime = Util.toMillis(minecraft.options.chunkSectionFadeInTime().get());
        final Vec3 camPos = camera.position();
        final double camX = camPos.x;
        final double camY = camPos.y;
        final double camZ = camPos.z;

        final Iterable<Entity> entitiesIterable = level.entitiesForRendering();
        final List<Entity> entities = new ArrayList<>();
        for (Entity e : entitiesIterable) {
            entities.add(e);
        }

        final int entityCount = entities.size();
        if (entityCount == 0) {
            levelRenderState.lastEntityRenderStateCount = 0;
            return true;
        }

        final TickRateManager tickRateManager = level.tickRateManager();
        final Entity cameraEntity = camera.entity();
        final boolean isDetached = camera.isDetached();
        final LocalPlayer player = minecraft.player;

        if (entityCount <= BATCH_SIZE) {
            for (int i = 0; i < entityCount; i++) {
                final Entity entity = entities.get(i);
                final boolean frozen = tickRateManager.isEntityFrozen(entity);
                final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(!frozen);

                if (isEntityVisible(entity, frustum, camX, camY, camZ, partialTick, chunkSectionFadeInTime, level, levelRenderer, player)) {
                    continue;
                }

                if (cameraEntity == entity && !isDetached) {
                    if (cameraEntity instanceof LivingEntity living && !living.isSleeping()) {
                        continue;
                    }
                }

                if (entity instanceof LocalPlayer && cameraEntity != entity) {
                    continue;
                }

                if (entity.tickCount == 0) {
                    entity.xOld = entity.getX();
                    entity.yOld = entity.getY();
                    entity.zOld = entity.getZ();
                }

                final EntityRenderState state = levelRenderer.entityRenderDispatcher().extractEntity(entity, partialTick);
                levelRenderState.entityRenderStates.add(state);
            }
        } else {
            final EntityExtractionTask rootTask = new EntityExtractionTask(
                    entities,
                    0,
                    entityCount,
                    tickRateManager,
                    deltaTracker,
                    frustum,
                    camX, camY, camZ,
                    chunkSectionFadeInTime,
                    level,
                    levelRenderer,
                    cameraEntity,
                    isDetached,
                    player
            );

            final List<EntityRenderState> extractedStates = ForkJoinPool.commonPool().invoke(rootTask);
            levelRenderState.entityRenderStates.addAll(extractedStates);
        }

        levelRenderState.lastEntityRenderStateCount = levelRenderState.entityRenderStates.size();
        return true;
    }

    private static boolean isEntityVisible(
            Entity entity,
            Frustum frustum,
            double camX, double camY, double camZ,
            float partialTick,
            long chunkSectionFadeInTime,
            ClientLevel level,
            LevelRenderer levelRenderer,
            LocalPlayer player
    ) {
        final boolean shouldRender = levelRenderer.entityRenderDispatcher().shouldRender(entity, frustum, camX, camY, camZ, partialTick);
        if (!shouldRender) {
            if (player == null || !entity.hasIndirectPassenger(player)) {
                return true;
            }
        }

        final BlockPos pos = entity.blockPosition();
        if (!level.isOutsideBuildHeight(pos.getY())) {
            if (!levelRenderer.isSectionCompiledAndVisible(pos, chunkSectionFadeInTime)) {
                return true;
            }
        }

        return false;
    }

    private static final class EntityExtractionTask extends RecursiveTask<List<EntityRenderState>> {
        private final List<Entity> entities;
        private final int start;
        private final int end;
        private final TickRateManager tickRateManager;
        private final DeltaTracker deltaTracker;
        private final Frustum frustum;
        private final double camX, camY, camZ;
        private final long chunkSectionFadeInTime;
        private final ClientLevel level;
        private final LevelRenderer levelRenderer;
        private final Entity cameraEntity;
        private final boolean isDetached;
        private final LocalPlayer player;

        EntityExtractionTask(
                List<Entity> entities,
                int start,
                int end,
                TickRateManager tickRateManager,
                DeltaTracker deltaTracker,
                Frustum frustum,
                double camX, double camY, double camZ,
                long chunkSectionFadeInTime,
                ClientLevel level,
                LevelRenderer levelRenderer,
                Entity cameraEntity,
                boolean isDetached,
                LocalPlayer player
        ) {
            this.entities = entities;
            this.start = start;
            this.end = end;
            this.tickRateManager = tickRateManager;
            this.deltaTracker = deltaTracker;
            this.frustum = frustum;
            this.camX = camX;
            this.camY = camY;
            this.camZ = camZ;
            this.chunkSectionFadeInTime = chunkSectionFadeInTime;
            this.level = level;
            this.levelRenderer = levelRenderer;
            this.cameraEntity = cameraEntity;
            this.isDetached = isDetached;
            this.player = player;
        }

        @Override
        protected List<EntityRenderState> compute() {
            final int count = this.end - this.start;
            if (count <= BATCH_SIZE) {
                final List<EntityRenderState> states = new ArrayList<>(count);
                for (int i = this.start; i < this.end; i++) {
                    final Entity entity = this.entities.get(i);
                    final boolean frozen = this.tickRateManager.isEntityFrozen(entity);
                    final float partialTick = this.deltaTracker.getGameTimeDeltaPartialTick(!frozen);

                    if (isEntityVisible(entity, this.frustum, this.camX, this.camY, this.camZ, partialTick, this.chunkSectionFadeInTime, this.level, this.levelRenderer, this.player)) {
                        continue;
                    }

                    if (this.cameraEntity == entity && !this.isDetached) {
                        if (this.cameraEntity instanceof LivingEntity living && !living.isSleeping()) {
                            continue;
                        }
                    }

                    if (entity instanceof LocalPlayer && this.cameraEntity != entity) {
                        continue;
                    }

                    if (entity.tickCount == 0) {
                        entity.xOld = entity.getX();
                        entity.yOld = entity.getY();
                        entity.zOld = entity.getZ();
                    }

                    final EntityRenderState state = this.levelRenderer.entityRenderDispatcher().extractEntity(entity, partialTick);
                    states.add(state);
                }
                return states;
            }

            final int mid = (this.start + this.end) >>> 1;
            final EntityExtractionTask left = new EntityExtractionTask(
                    this.entities, this.start, mid,
                    this.tickRateManager, this.deltaTracker, this.frustum,
                    this.camX, this.camY, this.camZ, this.chunkSectionFadeInTime,
                    this.level, this.levelRenderer, this.cameraEntity, this.isDetached, this.player
            );
            final EntityExtractionTask right = new EntityExtractionTask(
                    this.entities, mid, this.end,
                    this.tickRateManager, this.deltaTracker, this.frustum,
                    this.camX, this.camY, this.camZ, this.chunkSectionFadeInTime,
                    this.level, this.levelRenderer, this.cameraEntity, this.isDetached, this.player
            );

            left.fork();
            final List<EntityRenderState> rightResult = right.compute();
            final List<EntityRenderState> leftResult = left.join();

            leftResult.addAll(rightResult);
            return leftResult;
        }
    }
}
