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
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

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
    private final float[] vertexU = new float[4];
    private final float[] vertexV = new float[4];
    private final float[] blendedShades = new float[4];
    private final int[] blendedLights = new int[4];
    private final XenoQuadLightResult result = new XenoQuadLightResult();
    private final BakedQuadGeometry bakedGeometry = new BakedQuadGeometry();
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

    public XenoQuadLightResult computeAmbientOcclusion(
        BlockAndTintGetter level,
        BlockState state,
        BlockPos centerPosition,
        XenoQuadGeometry geometry,
        boolean enhanced
    ) {
        Direction direction = geometry.direction();

        float minX = 32.0F, minY = 32.0F, minZ = 32.0F;
        float maxX = -32.0F, maxY = -32.0F, maxZ = -32.0F;
        float minU = 1.0F, minV = 1.0F;
        float maxU = 0.0F, maxV = 0.0F;
        float[] vertexU = this.vertexU;
        float[] vertexV = this.vertexV;

        for (int i = 0; i < 4; i++) {
            float px = geometry.x(i);
            float py = geometry.y(i);
            float pz = geometry.z(i);
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
            maxZ = Math.max(maxZ, pz);

            float u = projectU(direction, px, pz);
            float v = projectV(direction, py, pz);
            vertexU[i] = u;
            vertexV[i] = v;
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }

        XenoQuadLightResult out = this.result;
        boolean isFullAligned = minU <= EPSILON && minV <= EPSILON && maxU >= 1.0F - EPSILON && maxV >= 1.0F - EPSILON;

        if (enhanced) {
            FaceData outer = faceData(level, state, centerPosition, direction, true);
            FaceData inner = faceData(level, state, centerPosition, direction, false);

            if (isFullAligned) {
                for (int i = 0; i < 4; i++) {
                    float depth = computeDepth(direction, geometry, i);
                    int corner = cornerIndex(vertexU[i], vertexV[i]);
                    out.shades[i] = Mth.lerp(depth, outer.shades[corner], inner.shades[corner]);
                    out.lightCoords[i] = lerpLight(outer.lightCoords[corner], inner.lightCoords[corner], depth);
                }
            } else {
                float[] blendedShades = this.blendedShades;
                int[] blendedLights = this.blendedLights;

                for (int i = 0; i < 4; i++) {
                    float depth = computeDepth(direction, geometry, i);
                    for (int corner = 0; corner < 4; corner++) {
                        blendedShades[corner] = Mth.lerp(depth, outer.shades[corner], inner.shades[corner]);
                        blendedLights[corner] = lerpLight(outer.lightCoords[corner], inner.lightCoords[corner], depth);
                    }
                    applyBarycentric(vertexU[i], vertexV[i], blendedShades, blendedLights, out, i);
                }
            }
        } else {
            boolean cubic = isFaceCubic(level, state, centerPosition, direction, minX, minY, minZ, maxX, maxY, maxZ);
            FaceData face = faceData(level, state, centerPosition, direction, cubic);

            if (isFullAligned) {
                for (int i = 0; i < 4; i++) {
                    int cornerIndex = cornerIndex(vertexU[i], vertexV[i]);
                    out.shades[i] = face.shades[cornerIndex];
                    out.lightCoords[i] = face.lightCoords[cornerIndex];
                }
            } else {
                for (int i = 0; i < 4; i++) {
                    applyBarycentric(vertexU[i], vertexV[i], face.shades, face.lightCoords, out, i);
                }
            }
        }

        out.directionalBrightness = getDirectionalBrightness(level.cardinalLighting(), geometry);
        return out;
    }

    public XenoQuadLightResult computeFlat(
        BlockAndTintGetter level,
        BlockState state,
        BlockPos centerPosition,
        XenoQuadGeometry geometry,
        @Nullable Direction cullFace,
        boolean enhanced
    ) {
        Direction lightFace = geometry.direction();
        Direction shadeOverride = geometry.shadeDirectionOverride();
        XenoQuadLightResult out = this.result;

        int lightCoords;
        float brightness;

        if (cullFace != null) {
            // Match vanilla by sampling the cull face when one is present.
            lightCoords = getOffsetLightmap(state, level, centerPosition, cullFace);
            brightness = getShade(level, lightFace, shadeOverride);
        } else if (isFaceCubic(level, state, centerPosition, lightFace, geometry)) {
            lightCoords = getOffsetLightmap(state, level, centerPosition, lightFace);
            brightness = getShade(level, lightFace, shadeOverride);
        } else {
            lightCoords = this.cache.getLightCoords(state, level, centerPosition);
            brightness = shadeOverride == null && enhanced
                ? getNormalVectorShade(level, geometry)
                : getShade(level, lightFace, shadeOverride);
        }

        for (int i = 0; i < 4; i++) {
            out.shades[i] = brightness;
            out.lightCoords[i] = lightCoords;
        }
        out.directionalBrightness = 1.0F;
        return out;
    }

    private int getOffsetLightmap(BlockState originState, BlockAndTintGetter level, BlockPos pos, Direction face) {
        if (originState.emissiveRendering()) {
            return LightCoordsUtil.FULL_BRIGHT;
        }

        int adjacent = this.cache.getRawLightCoords(level, this.scratchPos.setWithOffset(pos, face));
        int block = Math.max(LightCoordsUtil.block(adjacent), originState.getLightEmission());
        int sky = LightCoordsUtil.sky(adjacent);
        return LightCoordsUtil.pack(block, sky);
    }

    private static float getShade(BlockAndTintGetter level, Direction lightFace, @Nullable Direction shadeOverride) {
        return level.cardinalLighting().byFace(shadeOverride != null ? shadeOverride : lightFace);
    }

    private static boolean isFaceCubic(
        BlockAndTintGetter level, BlockState state, BlockPos pos, Direction direction, XenoQuadGeometry geometry
    ) {
        float minX = 32.0F, minY = 32.0F, minZ = 32.0F;
        float maxX = -32.0F, maxY = -32.0F, maxZ = -32.0F;

        for (int i = 0; i < 4; i++) {
            float px = geometry.x(i);
            float py = geometry.y(i);
            float pz = geometry.z(i);
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
            maxZ = Math.max(maxZ, pz);
        }

        return isFaceCubic(level, state, pos, direction, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static float getNormalVectorShade(BlockAndTintGetter level, XenoQuadGeometry geometry) {
        float dx0 = geometry.x(2) - geometry.x(0);
        float dy0 = geometry.y(2) - geometry.y(0);
        float dz0 = geometry.z(2) - geometry.z(0);
        float dx1 = geometry.x(3) - geometry.x(1);
        float dy1 = geometry.y(3) - geometry.y(1);
        float dz1 = geometry.z(3) - geometry.z(1);

        float nx = dy0 * dz1 - dz0 * dy1;
        float ny = dz0 * dx1 - dx0 * dz1;
        float nz = dx0 * dy1 - dy0 * dx1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        if (len > 1.0E-6F) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }

        CardinalLighting lighting = level.cardinalLighting();
        float sum = 0.0F;
        float div = 0.0F;

        if (nx > 0.0F) {
            sum += nx * lighting.byFace(Direction.EAST);
            div += nx;
        } else if (nx < 0.0F) {
            sum += -nx * lighting.byFace(Direction.WEST);
            div -= nx;
        }

        if (ny > 0.0F) {
            sum += ny * lighting.byFace(Direction.UP);
            div += ny;
        } else if (ny < 0.0F) {
            sum += -ny * lighting.byFace(Direction.DOWN);
            div -= ny;
        }

        if (nz > 0.0F) {
            sum += nz * lighting.byFace(Direction.SOUTH);
            div += nz;
        } else if (nz < 0.0F) {
            sum += -nz * lighting.byFace(Direction.NORTH);
            div -= nz;
        }

        return div == 0.0F ? 1.0F : sum / div;
    }

    private static void applyBarycentric(
        float rawU, float rawV, float[] shades, int[] lightCoords, XenoQuadLightResult out, int vertex
    ) {
        float u = Mth.clamp(rawU, 0.0F, 1.0F);
        float v = Mth.clamp(rawV, 0.0F, 1.0F);

        float w00 = (1.0F - u) * (1.0F - v);
        float w01 = (1.0F - u) * v;
        float w11 = u * v;
        float w10 = u * (1.0F - v);

        float shade = w00 * shades[0] + w01 * shades[1] + w11 * shades[3] + w10 * shades[2];
        out.shades[vertex] = Mth.clamp(shade, 0.0F, 1.0F);
        out.lightCoords[vertex] = LightCoordsUtil.smoothWeightedBlend(
            lightCoords[0], lightCoords[1], lightCoords[3], lightCoords[2],
            w00, w01, w11, w10
        );
    }

    public void prepareQuadAmbientOcclusion(
        BlockAndTintGetter level, BlockState state, BlockPos centerPosition, BakedQuad quad, QuadInstance outputInstance
    ) {
        XenoQuadLightResult result = computeAmbientOcclusion(
            level, state, centerPosition, this.bakedGeometry.bind(quad), false
        );

        for (int i = 0; i < 4; i++) {
            outputInstance.setColor(i, ARGB.gray(result.shades[i]));
            outputInstance.setLightCoords(i, result.lightCoords[i]);
        }

        outputInstance.scaleColor(result.directionalBrightness);
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

    private FaceData faceData(
        BlockAndTintGetter level, BlockState state, BlockPos centerPosition, Direction direction, boolean cubic
    ) {
        if (cubic) {
            FaceData face = this.cubicFaceCache[direction.get3DDataValue()];
            if (!face.computed) {
                this.computeFaceLighting(level, state, centerPosition, direction, true, face);
                face.computed = true;
            }
            return face;
        }

        FaceData face = this.nonCubicFace;
        this.computeFaceLighting(level, state, centerPosition, direction, false, face);
        return face;
    }

    private static int cornerIndex(float u, float v) {
        return (u > 0.5F ? 2 : 0) | (v > 0.5F ? 1 : 0);
    }

    private static float computeDepth(Direction face, XenoQuadGeometry geometry, int vertexIndex) {
        float value = switch (face) {
            case DOWN -> geometry.y(vertexIndex);
            case UP -> 1.0F - geometry.y(vertexIndex);
            case NORTH -> geometry.z(vertexIndex);
            case SOUTH -> 1.0F - geometry.z(vertexIndex);
            case WEST -> geometry.x(vertexIndex);
            case EAST -> 1.0F - geometry.x(vertexIndex);
        };
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static int lerpLight(int a, int b, float t) {
        int blockA = LightCoordsUtil.block(a);
        int skyA = LightCoordsUtil.sky(a);
        int blockB = LightCoordsUtil.block(b);
        int skyB = LightCoordsUtil.sky(b);
        int block = Math.round(blockA + (blockB - blockA) * t);
        int sky = Math.round(skyA + (skyB - skyA) * t);
        return LightCoordsUtil.pack(block, sky);
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

    private static float getDirectionalBrightness(CardinalLighting cardinalLighting, XenoQuadGeometry geometry) {
        Direction override = geometry.shadeDirectionOverride();
        return cardinalLighting.byFace(override != null ? override : geometry.direction());
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

        float centerShade = this.cache.getShadeBrightness(level, basePosition);

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

    private static float projectU(Direction face, float x, float z) {
        return switch (face) {
            case DOWN, UP, NORTH, SOUTH -> x;
            case WEST, EAST -> z;
        };
    }

    private static float projectV(Direction face, float y, float z) {
        return switch (face) {
            case DOWN, UP -> z;
            case NORTH, SOUTH, WEST, EAST -> y;
        };
    }

    private static final class BakedQuadGeometry implements XenoQuadGeometry {
        private final float[] xs = new float[4];
        private final float[] ys = new float[4];
        private final float[] zs = new float[4];
        private BakedQuad quad;
        private Direction direction;
        private @Nullable Direction shadeDirectionOverride;

        BakedQuadGeometry bind(BakedQuad quad) {
            this.quad = quad;
            this.direction = quad.direction();
            this.shadeDirectionOverride = quad.materialInfo().shadeDirectionOverride();
            for (int i = 0; i < 4; i++) {
                Vector3fc position = quad.position(i);
                this.xs[i] = position.x();
                this.ys[i] = position.y();
                this.zs[i] = position.z();
            }
            return this;
        }

        @Override
        public Direction direction() {
            return this.direction;
        }

        @Override
        public @Nullable Direction shadeDirectionOverride() {
            return this.shadeDirectionOverride;
        }

        @Override
        public float x(int vertexIndex) {
            return this.xs[vertexIndex];
        }

        @Override
        public float y(int vertexIndex) {
            return this.ys[vertexIndex];
        }

        @Override
        public float z(int vertexIndex) {
            return this.zs[vertexIndex];
        }
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
            float shadeA = cache.getShadeBrightness(level, posA);

            posB.setWithOffset(basePos, dirB);
            BlockState stateB = cache.getState(level, posB);
            int lightB = cache.getLightCoords(stateB, level, posB);
            float shadeB = cache.getShadeBrightness(level, posB);

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
                shadeDiag = cache.getShadeBrightness(level, posDiag);
            }

            this.lightCoords[cornerIndex] = LightCoordsUtil.smoothBlend(lightA, lightB, lightDiag, centerLight);
            this.shades[cornerIndex] = (shadeA + shadeB + shadeDiag + centerShade) * 0.25F;
        }
    }
}
