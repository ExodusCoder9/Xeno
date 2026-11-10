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

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public final class XenoParallelBlockEntityExtractor {
    public static boolean ENABLED = true;
    private static final int BATCH_SIZE = 16;
    private static final ThreadLocal<PoseStack> THREAD_POSE_STACK = ThreadLocal.withInitial(PoseStack::new);

    private XenoParallelBlockEntityExtractor() {
    }
    public static boolean extractBlockEntities(
            Minecraft minecraft,
            ClientLevel level,
            LevelRenderer levelRenderer,
            Camera camera,
            float partialTick,
            LevelRenderState levelRenderState
    ) {
        if (!ENABLED || level == null || levelRenderer == null) {
            return false;
        }

        final Vec3 camPos = camera.position();
        final double camX = camPos.x;
        final double camY = camPos.y;
        final double camZ = camPos.z;

        final long chunkSectionFadeInTime = Util.toMillis(minecraft.options.chunkSectionFadeInTime().get());
        final long now = Util.getMillis();

        final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = levelRenderer.visibleSections();
        final List<SectionRenderDispatcher.RenderSection> activeSections = new ArrayList<>();
        int totalBlockEntities = 0;

        final int sectionCount = visibleSections.size();
        for (int i = 0; i < sectionCount; i++) {
            final SectionRenderDispatcher.RenderSection section = visibleSections.get(i);
            final SectionMesh mesh = section.getSectionMesh();
            final List<BlockEntity> blockEntities = mesh.getRenderableBlockEntities();
            if (blockEntities.isEmpty()) {
                continue;
            }
            if (section.getVisibility(now, chunkSectionFadeInTime) < 0.3f) {
                continue;
            }
            activeSections.add(section);
            totalBlockEntities += blockEntities.size();
        }

        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress = level.destructionProgress();
        final BlockEntityRenderDispatcher dispatcher = levelRenderer.blockEntityRenderDispatcher();

        if (totalBlockEntities <= BATCH_SIZE) {
            final PoseStack poseStack = THREAD_POSE_STACK.get();
            for (SectionRenderDispatcher.RenderSection section : activeSections) {
                final List<BlockEntity> blockEntities = section.getSectionMesh().getRenderableBlockEntities();
                extractSectionBlockEntities(blockEntities, destructionProgress, dispatcher, partialTick, camX, camY, camZ, poseStack, levelRenderState.blockEntityRenderStates);
            }
        } else {
            final BlockEntityExtractionTask rootTask = new BlockEntityExtractionTask(
                    activeSections,
                    0,
                    activeSections.size(),
                    destructionProgress,
                    dispatcher,
                    partialTick,
                    camX, camY, camZ
            );

            final List<BlockEntityRenderState> extractedStates = ForkJoinPool.commonPool().invoke(rootTask);
            levelRenderState.blockEntityRenderStates.addAll(extractedStates);
        }
        final Set<BlockEntity> globallyRendered = level.getGloballyRenderedBlockEntities();
        for (Iterator<BlockEntity> it = globallyRendered.iterator(); it.hasNext(); ) {
            final BlockEntity be = it.next();
            if (be.isRemoved()) {
                it.remove();
                continue;
            }
            final BlockEntityRenderState state = dispatcher.tryExtractRenderState(be, partialTick, null, true);
            if (state != null) {
                levelRenderState.blockEntityRenderStates.add(state);
            }
        }

        return true;
    }

    private static void extractSectionBlockEntities(
            List<BlockEntity> blockEntities,
            Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress,
            BlockEntityRenderDispatcher dispatcher,
            float partialTick,
            double camX, double camY, double camZ,
            PoseStack poseStack,
            List<BlockEntityRenderState> output
    ) {
        final int count = blockEntities.size();
        for (int i = 0; i < count; i++) {
            final BlockEntity blockEntity = blockEntities.get(i);
            final BlockPos pos = blockEntity.getBlockPos();

            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay = null;
            final SortedSet<BlockDestructionProgress> progress = destructionProgress.get(pos.asLong());
            if (progress != null && !progress.isEmpty()) {
                poseStack.pushPose();
                poseStack.translate((double) pos.getX() - camX, (double) pos.getY() - camY, (double) pos.getZ() - camZ);
                crumblingOverlay = new ModelFeatureRenderer.CrumblingOverlay(progress.last().getProgress(), poseStack.last());
                poseStack.popPose();
            }

            final BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, partialTick, crumblingOverlay, false);
            if (state != null) {
                output.add(state);
            }
        }
    }

    private static final class BlockEntityExtractionTask extends RecursiveTask<List<BlockEntityRenderState>> {
        private final List<SectionRenderDispatcher.RenderSection> sections;
        private final int start;
        private final int end;
        private final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;
        private final BlockEntityRenderDispatcher dispatcher;
        private final float partialTick;
        private final double camX, camY, camZ;

        BlockEntityExtractionTask(
                List<SectionRenderDispatcher.RenderSection> sections,
                int start,
                int end,
                Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress,
                BlockEntityRenderDispatcher dispatcher,
                float partialTick,
                double camX, double camY, double camZ
        ) {
            this.sections = sections;
            this.start = start;
            this.end = end;
            this.destructionProgress = destructionProgress;
            this.dispatcher = dispatcher;
            this.partialTick = partialTick;
            this.camX = camX;
            this.camY = camY;
            this.camZ = camZ;
        }

        @Override
        protected List<BlockEntityRenderState> compute() {
            final int count = this.end - this.start;
            if (count <= 2) {
                final List<BlockEntityRenderState> states = new ArrayList<>();
                final PoseStack poseStack = THREAD_POSE_STACK.get();
                for (int i = this.start; i < this.end; i++) {
                    final SectionRenderDispatcher.RenderSection section = this.sections.get(i);
                    final List<BlockEntity> blockEntities = section.getSectionMesh().getRenderableBlockEntities();
                    extractSectionBlockEntities(
                            blockEntities,
                            this.destructionProgress,
                            this.dispatcher,
                            this.partialTick,
                            this.camX, this.camY, this.camZ,
                            poseStack,
                            states
                    );
                }
                return states;
            }

            final int mid = (this.start + this.end) >>> 1;
            final BlockEntityExtractionTask left = new BlockEntityExtractionTask(
                    this.sections, this.start, mid,
                    this.destructionProgress, this.dispatcher,
                    this.partialTick, this.camX, this.camY, this.camZ
            );
            final BlockEntityExtractionTask right = new BlockEntityExtractionTask(
                    this.sections, mid, this.end,
                    this.destructionProgress, this.dispatcher,
                    this.partialTick, this.camX, this.camY, this.camZ
            );

            left.fork();
            final List<BlockEntityRenderState> rightResult = right.compute();
            final List<BlockEntityRenderState> leftResult = left.join();

            leftResult.addAll(rightResult);
            return leftResult;
        }
    }
}
