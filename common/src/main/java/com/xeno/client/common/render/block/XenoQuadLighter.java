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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.common.render.block;

import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.QuadInstance;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;

public final class XenoQuadLighter {
    public static final int CHECK_LIGHT = -1;
    private static final float EPSILON = 1.0E-4F;
    private final XenoLightDataCache cache;
    private final MutableBlockPos scratchPos = new MutableBlockPos();
    private final MutableBlockPos baseScratchPos = new MutableBlockPos();
    private final MutableBlockPos neighborScratchPos = new MutableBlockPos();
    private final MutableBlockPos sideAPos = new MutableBlockPos();
    private final MutableBlockPos sideBPos = new MutableBlockPos();
    private final MutableBlockPos cornerDiagPos = new MutableBlockPos();
    private final MutableBlockPos permScratchPos = new MutableBlockPos();
    private final FaceData[] cubicFaceCache = new FaceData[6];
    private final FaceData nonCubicFace = new FaceData();
    private long cachedBlockPos = Long.MIN_VALUE;

    public XenoQuadLighter(XenoLightDataCache cache) {
        this.cache = cache;
        for (int i = 0; i < 6; i++) {
            this.cubicFaceCache[i] = new FaceData();
        }
    }

    public void resetForBlock(BlockPos pos) {
        long packed = pos.asLong();
        if (this.cachedBlockPos != packed) {
            this.cachedBlockPos = packed;
            for (int i = 0; i < 6; i++) {
                this.cubicFaceCache[i].computed = false;
            }
            this.nonCubicFace.computed = false;
        }
    }

    public int getLightCoords(BlockState state, BlockAndTintGetter level, BlockPos relativePos) {
        return this.cache.getLightCoords(state, level, relativePos);
    }

    public void prepareQuadAmbientOcclusion(
        BlockAndTintGetter level, BlockState state, BlockPos centerPosition, BakedQuad quad, QuadInstance outputInstance
    ) {
        Direction direction = quad.direction();
        int faceIndex = direction.get3DDataValue();

        float minX = 32.0F, minY = 32.0F, minZ = 32.0F;
        float maxX = -32.0F, maxY = -32.0F, maxZ = -32.0F;
        float minU = 1.0F, minV = 1.0F;
        float maxU = 0.0F, maxV = 0.0F;
        float[] vertexU = new float[4];
        float[] vertexV = new float[4];

        for (int i = 0; i < 4; i++) {
            Vector3fc pos = quad.position(i);
            float px = pos.x();
            float py = pos.y();
            float pz = pos.z();
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
            maxZ = Math.max(maxZ, pz);

            float u = projectU(direction, px, py, pz);
            float v = projectV(direction, px, py, pz);
            vertexU[i] = u;
            vertexV[i] = v;
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }

        boolean cubic = isFaceCubic(level, state, centerPosition, direction, minX, minY, minZ, maxX, maxY, maxZ);
        FaceData face;
        if (cubic) {
            face = this.cubicFaceCache[faceIndex];
            if (!face.computed) {
                this.computeFaceLighting(level, state, centerPosition, direction, true, face);
                face.computed = true;
            }
        } else {
            face = this.nonCubicFace;
            this.computeFaceLighting(level, state, centerPosition, direction, false, face);
        }

        boolean isFullAligned = minU <= EPSILON && minV <= EPSILON && maxU >= 1.0F - EPSILON && maxV >= 1.0F - EPSILON;

        if (isFullAligned) {
            for (int i = 0; i < 4; i++) {
                int cornerIndex = (vertexU[i] > 0.5F ? 2 : 0) | (vertexV[i] > 0.5F ? 1 : 0);
                outputInstance.setColor(i, ARGB.gray(face.shades[cornerIndex]));
                outputInstance.setLightCoords(i, face.lightCoords[cornerIndex]);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                float u = Mth.clamp(vertexU[i], 0.0F, 1.0F);
                float v = Mth.clamp(vertexV[i], 0.0F, 1.0F);

                float w00 = (1.0F - u) * (1.0F - v);
                float w01 = (1.0F - u) * v;
                float w11 = u * v;
                float w10 = u * (1.0F - v);

                float shade = w00 * face.shades[0] + w01 * face.shades[1] + w11 * face.shades[3] + w10 * face.shades[2];
                int light = LightCoordsUtil.smoothWeightedBlend(
                    face.lightCoords[0], face.lightCoords[1], face.lightCoords[3], face.lightCoords[2],
                    w00, w01, w11, w10
                );

                outputInstance.setColor(i, ARGB.gray(Mth.clamp(shade, 0.0F, 1.0F)));
                outputInstance.setLightCoords(i, light);
            }
        }

        CardinalLighting cardinalLighting = level.cardinalLighting();
        outputInstance.scaleColor(getDirectionalBrightness(cardinalLighting, quad, direction));
    }

    public void prepareQuadFlat(
        BlockAndTintGetter level, BlockState state, BlockPos pos, int lightCoords, BakedQuad quad, QuadInstance outputInstance
    ) {
        if (lightCoords == CHECK_LIGHT) {
            Direction direction = quad.direction();
            float minX = 32.0F, minY = 32.0F, minZ = 32.0F;
            float maxX = -32.0F, maxY = -32.0F, maxZ = -32.0F;
            for (int i = 0; i < 4; i++) {
                Vector3fc p = quad.position(i);
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                minZ = Math.min(minZ, p.z());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
                maxZ = Math.max(maxZ, p.z());
            }
            boolean cubic = isFaceCubic(level, state, pos, direction, minX, minY, minZ, maxX, maxY, maxZ);
            BlockPos lightPos = cubic ? this.scratchPos.setWithOffset(pos, direction) : pos;
            outputInstance.setLightCoords(this.cache.getLightCoords(state, level, lightPos));
        } else {
            outputInstance.setLightCoords(lightCoords);
        }

        CardinalLighting cardinalLighting = level.cardinalLighting();
        outputInstance.setColor(ARGB.gray(getDirectionalBrightness(cardinalLighting, quad, quad.direction())));
    }

    private static boolean isFaceCubic(
        BlockAndTintGetter level, BlockState state, BlockPos pos, Direction direction,
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ
    ) {
        return switch (direction) {
            case DOWN -> minY == maxY && (minY < EPSILON || state.isCollisionShapeFullBlock(level, pos));
            case UP -> minY == maxY && (maxY > 1.0F - EPSILON || state.isCollisionShapeFullBlock(level, pos));
            case NORTH -> minZ == maxZ && (minZ < EPSILON || state.isCollisionShapeFullBlock(level, pos));
            case SOUTH -> minZ == maxZ && (maxZ > 1.0F - EPSILON || state.isCollisionShapeFullBlock(level, pos));
            case WEST -> minX == maxX && (minX < EPSILON || state.isCollisionShapeFullBlock(level, pos));
            case EAST -> minX == maxX && (maxX > 1.0F - EPSILON || state.isCollisionShapeFullBlock(level, pos));
        };
    }

    private static float getDirectionalBrightness(CardinalLighting cardinalLighting, BakedQuad quad, Direction actualDirection) {
        Direction shadeDirectionOverride = quad.materialInfo().shadeDirectionOverride();
        return shadeDirectionOverride != null ? cardinalLighting.byFace(shadeDirectionOverride) : cardinalLighting.byFace(actualDirection);
    }

    private void computeFaceLighting(
        BlockAndTintGetter level, BlockState state, BlockPos centerPosition, Direction direction, boolean cubic, FaceData out
    ) {
        BlockPos basePosition = cubic ? this.baseScratchPos.setWithOffset(centerPosition, direction) : centerPosition;
        Direction uDir = getFaceTangentU(direction);
        Direction vDir = getFaceTangentV(direction);

        BlockPos neighborPos = this.neighborScratchPos.setWithOffset(centerPosition, direction);
        BlockState neighborState = this.cache.getState(level, neighborPos);

        int centerLight = this.cache.getLightCoords(state, level, centerPosition);
        if (cubic || !neighborState.isSolidRender()) {
            centerLight = this.cache.getLightCoords(neighborState, level, neighborPos);
        }

        BlockState baseState = cubic ? neighborState : state;
        float centerShade = this.cache.getShadeBrightness(baseState, level, basePosition);

        out.computeCorner(0, level, this.cache, basePosition, direction, uDir.getOpposite(), vDir.getOpposite(), centerLight, centerShade, this.sideAPos, this.sideBPos, this.cornerDiagPos, this.permScratchPos);
        out.computeCorner(1, level, this.cache, basePosition, direction, uDir.getOpposite(), vDir, centerLight, centerShade, this.sideAPos, this.sideBPos, this.cornerDiagPos, this.permScratchPos);
        out.computeCorner(2, level, this.cache, basePosition, direction, uDir, vDir.getOpposite(), centerLight, centerShade, this.sideAPos, this.sideBPos, this.cornerDiagPos, this.permScratchPos);
        out.computeCorner(3, level, this.cache, basePosition, direction, uDir, vDir, centerLight, centerShade, this.sideAPos, this.sideBPos, this.cornerDiagPos, this.permScratchPos);
    }

    private static Direction getFaceTangentU(Direction face) {
        return switch (face) {
            case DOWN, UP, NORTH, SOUTH -> Direction.EAST;
            case WEST, EAST -> Direction.SOUTH;
        };
    }

    private static Direction getFaceTangentV(Direction face) {
        return switch (face) {
            case DOWN, UP -> Direction.SOUTH;
            case NORTH, SOUTH, WEST, EAST -> Direction.UP;
        };
    }

    private static float projectU(Direction face, float x, float y, float z) {
        return switch (face) {
            case DOWN, UP, NORTH, SOUTH -> x;
            case WEST, EAST -> z;
        };
    }

    private static float projectV(Direction face, float x, float y, float z) {
        return switch (face) {
            case DOWN, UP -> z;
            case NORTH, SOUTH, WEST, EAST -> y;
        };
    }

    private static final class FaceData {
        boolean computed;
        final int[] lightCoords = new int[4];
        final float[] shades = new float[4];

        void computeCorner(
            int cornerIndex,
            BlockAndTintGetter level,
            XenoLightDataCache cache,
            BlockPos basePos,
            Direction faceDir,
            Direction dirA,
            Direction dirB,
            int centerLight,
            float centerShade,
            MutableBlockPos posA,
            MutableBlockPos posB,
            MutableBlockPos posDiag,
            MutableBlockPos permScratch
        ) {
            posA.setWithOffset(basePos, dirA);
            BlockState stateA = cache.getState(level, posA);
            int lightA = cache.getLightCoords(stateA, level, posA);
            float shadeA = cache.getShadeBrightness(stateA, level, posA);

            posB.setWithOffset(basePos, dirB);
            BlockState stateB = cache.getState(level, posB);
            int lightB = cache.getLightCoords(stateB, level, posB);
            float shadeB = cache.getShadeBrightness(stateB, level, posB);

            boolean permeableA = cache.getState(level, permScratch.setWithOffset(posA, faceDir)).isLightPermeable();
            boolean permeableB = cache.getState(level, permScratch.setWithOffset(posB, faceDir)).isLightPermeable();

            int lightDiag;
            float shadeDiag;
            if (!permeableA && !permeableB) {
                lightDiag = lightA;
                shadeDiag = shadeA;
            } else {
                posDiag.setWithOffset(basePos, dirA).move(dirB);
                BlockState stateDiag = cache.getState(level, posDiag);
                lightDiag = cache.getLightCoords(stateDiag, level, posDiag);
                shadeDiag = cache.getShadeBrightness(stateDiag, level, posDiag);
            }

            this.lightCoords[cornerIndex] = LightCoordsUtil.smoothBlend(lightA, lightB, lightDiag, centerLight);
            this.shades[cornerIndex] = (shadeA + shadeB + shadeDiag + centerShade) * 0.25F;
        }
    }
}
