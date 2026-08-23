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
import net.minecraft.util.Util;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

public final class XenoQuadLighter {
    public static final int CHECK_LIGHT = -1;

    private final XenoLightDataCache cache;
    private final MutableBlockPos scratchPos = new MutableBlockPos();
    private boolean faceCubic;
    private boolean facePartial;
    private final float[] faceShape = new float[SizeInformation.COUNT];

    public XenoQuadLighter(XenoLightDataCache cache) {
        this.cache = cache;
    }

    public int getLightCoords(BlockState state, BlockAndTintGetter level, BlockPos relativePos) {
        return this.cache.getLightCoords(state, level, relativePos);
    }

    public void prepareQuadAmbientOcclusion(
        BlockAndTintGetter level, BlockState state, BlockPos centerPosition, BakedQuad quad, QuadInstance outputInstance
    ) {
        this.prepareQuadShape(level, state, centerPosition, quad, true);
        Direction direction = quad.direction();
        BlockPos basePosition = this.faceCubic ? centerPosition.relative(direction) : centerPosition;
        AdjacencyInfo info = AdjacencyInfo.fromFacing(direction);
        MutableBlockPos pos = this.scratchPos;
        pos.setWithOffset(basePosition, info.corners[0]);
        BlockState state0 = this.cache.getState(level, pos);
        int light0 = this.cache.getLightCoords(state0, level, pos);
        float shade0 = this.cache.getShadeBrightness(state0, level, pos);
        pos.setWithOffset(basePosition, info.corners[1]);
        BlockState state1 = this.cache.getState(level, pos);
        int light1 = this.cache.getLightCoords(state1, level, pos);
        float shade1 = this.cache.getShadeBrightness(state1, level, pos);
        pos.setWithOffset(basePosition, info.corners[2]);
        BlockState state2 = this.cache.getState(level, pos);
        int light2 = this.cache.getLightCoords(state2, level, pos);
        float shade2 = this.cache.getShadeBrightness(state2, level, pos);
        pos.setWithOffset(basePosition, info.corners[3]);
        BlockState state3 = this.cache.getState(level, pos);
        int light3 = this.cache.getLightCoords(state3, level, pos);
        float shade3 = this.cache.getShadeBrightness(state3, level, pos);
        BlockState corner0 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[0]).move(direction));
        boolean translucent0 = !corner0.isViewBlocking(level, pos) || corner0.getLightDampening() == 0;
        BlockState corner1 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[1]).move(direction));
        boolean translucent1 = !corner1.isViewBlocking(level, pos) || corner1.getLightDampening() == 0;
        BlockState corner2 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[2]).move(direction));
        boolean translucent2 = !corner2.isViewBlocking(level, pos) || corner2.getLightDampening() == 0;
        BlockState corner3 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[3]).move(direction));
        boolean translucent3 = !corner3.isViewBlocking(level, pos) || corner3.getLightDampening() == 0;
        float shadeCorner02;
        int lightCorner02;
        if (!translucent2 && !translucent0) {
            shadeCorner02 = shade0;
            lightCorner02 = light0;
        } else {
            pos.setWithOffset(basePosition, info.corners[0]).move(info.corners[2]);
            BlockState state02 = this.cache.getState(level, pos);
            shadeCorner02 = this.cache.getShadeBrightness(state02, level, pos);
            lightCorner02 = this.cache.getLightCoords(state02, level, pos);
        }

        float shadeCorner03;
        int lightCorner03;
        if (!translucent3 && !translucent0) {
            shadeCorner03 = shade0;
            lightCorner03 = light0;
        } else {
            pos.setWithOffset(basePosition, info.corners[0]).move(info.corners[3]);
            BlockState state03 = this.cache.getState(level, pos);
            shadeCorner03 = this.cache.getShadeBrightness(state03, level, pos);
            lightCorner03 = this.cache.getLightCoords(state03, level, pos);
        }

        float shadeCorner12;
        int lightCorner12;
        if (!translucent2 && !translucent1) {
            shadeCorner12 = shade0;
            lightCorner12 = light0;
        } else {
            pos.setWithOffset(basePosition, info.corners[1]).move(info.corners[2]);
            BlockState state12 = this.cache.getState(level, pos);
            shadeCorner12 = this.cache.getShadeBrightness(state12, level, pos);
            lightCorner12 = this.cache.getLightCoords(state12, level, pos);
        }

        float shadeCorner13;
        int lightCorner13;
        if (!translucent3 && !translucent1) {
            shadeCorner13 = shade0;
            lightCorner13 = light0;
        } else {
            pos.setWithOffset(basePosition, info.corners[1]).move(info.corners[3]);
            BlockState state13 = this.cache.getState(level, pos);
            shadeCorner13 = this.cache.getShadeBrightness(state13, level, pos);
            lightCorner13 = this.cache.getLightCoords(state13, level, pos);
        }

        int lightCenter = this.cache.getLightCoords(state, level, centerPosition);
        pos.setWithOffset(centerPosition, direction);
        BlockState nextState = this.cache.getState(level, pos);
        if (this.faceCubic || !nextState.isSolidRender()) {
            lightCenter = this.cache.getLightCoords(nextState, level, pos);
        }

        float shadeCenter = this.faceCubic
            ? this.cache.getShadeBrightness(this.cache.getState(level, basePosition), level, basePosition)
            : this.cache.getShadeBrightness(this.cache.getState(level, centerPosition), level, centerPosition);
        AmbientVertexRemap remap = AmbientVertexRemap.fromFacing(direction);
        if (this.facePartial && info.doNonCubicWeight) {
            float tempShade1 = (shade3 + shade0 + shadeCorner03 + shadeCenter) * 0.25F;
            float tempShade2 = (shade2 + shade0 + shadeCorner02 + shadeCenter) * 0.25F;
            float tempShade3 = (shade2 + shade1 + shadeCorner12 + shadeCenter) * 0.25F;
            float tempShade4 = (shade3 + shade1 + shadeCorner13 + shadeCenter) * 0.25F;
            float vert0weight01 = this.faceShape[info.vert0Weights[0].index] * this.faceShape[info.vert0Weights[1].index];
            float vert0weight23 = this.faceShape[info.vert0Weights[2].index] * this.faceShape[info.vert0Weights[3].index];
            float vert0weight45 = this.faceShape[info.vert0Weights[4].index] * this.faceShape[info.vert0Weights[5].index];
            float vert0weight67 = this.faceShape[info.vert0Weights[6].index] * this.faceShape[info.vert0Weights[7].index];
            float vert1weight01 = this.faceShape[info.vert1Weights[0].index] * this.faceShape[info.vert1Weights[1].index];
            float vert1weight23 = this.faceShape[info.vert1Weights[2].index] * this.faceShape[info.vert1Weights[3].index];
            float vert1weight45 = this.faceShape[info.vert1Weights[4].index] * this.faceShape[info.vert1Weights[5].index];
            float vert1weight67 = this.faceShape[info.vert1Weights[6].index] * this.faceShape[info.vert1Weights[7].index];
            float vert2weight01 = this.faceShape[info.vert2Weights[0].index] * this.faceShape[info.vert2Weights[1].index];
            float vert2weight23 = this.faceShape[info.vert2Weights[2].index] * this.faceShape[info.vert2Weights[3].index];
            float vert2weight45 = this.faceShape[info.vert2Weights[4].index] * this.faceShape[info.vert2Weights[5].index];
            float vert2weight67 = this.faceShape[info.vert2Weights[6].index] * this.faceShape[info.vert2Weights[7].index];
            float vert3weight01 = this.faceShape[info.vert3Weights[0].index] * this.faceShape[info.vert3Weights[1].index];
            float vert3weight23 = this.faceShape[info.vert3Weights[2].index] * this.faceShape[info.vert3Weights[3].index];
            float vert3weight45 = this.faceShape[info.vert3Weights[4].index] * this.faceShape[info.vert3Weights[5].index];
            float vert3weight67 = this.faceShape[info.vert3Weights[6].index] * this.faceShape[info.vert3Weights[7].index];
            outputInstance.setColor(
                remap.vert0,
                ARGB.gray(Math.clamp(tempShade1 * vert0weight01 + tempShade2 * vert0weight23 + tempShade3 * vert0weight45 + tempShade4 * vert0weight67, 0.0F, 1.0F))
            );
            outputInstance.setColor(
                remap.vert1,
                ARGB.gray(Math.clamp(tempShade1 * vert1weight01 + tempShade2 * vert1weight23 + tempShade3 * vert1weight45 + tempShade4 * vert1weight67, 0.0F, 1.0F))
            );
            outputInstance.setColor(
                remap.vert2,
                ARGB.gray(Math.clamp(tempShade1 * vert2weight01 + tempShade2 * vert2weight23 + tempShade3 * vert2weight45 + tempShade4 * vert2weight67, 0.0F, 1.0F))
            );
            outputInstance.setColor(
                remap.vert3,
                ARGB.gray(Math.clamp(tempShade1 * vert3weight01 + tempShade2 * vert3weight23 + tempShade3 * vert3weight45 + tempShade4 * vert3weight67, 0.0F, 1.0F))
            );
            int tc1 = LightCoordsUtil.smoothBlend(light3, light0, lightCorner03, lightCenter);
            int tc2 = LightCoordsUtil.smoothBlend(light2, light0, lightCorner02, lightCenter);
            int tc3 = LightCoordsUtil.smoothBlend(light2, light1, lightCorner12, lightCenter);
            int tc4 = LightCoordsUtil.smoothBlend(light3, light1, lightCorner13, lightCenter);
            outputInstance.setLightCoords(
                remap.vert0, LightCoordsUtil.smoothWeightedBlend(tc1, tc2, tc3, tc4, vert0weight01, vert0weight23, vert0weight45, vert0weight67)
            );
            outputInstance.setLightCoords(
                remap.vert1, LightCoordsUtil.smoothWeightedBlend(tc1, tc2, tc3, tc4, vert1weight01, vert1weight23, vert1weight45, vert1weight67)
            );
            outputInstance.setLightCoords(
                remap.vert2, LightCoordsUtil.smoothWeightedBlend(tc1, tc2, tc3, tc4, vert2weight01, vert2weight23, vert2weight45, vert2weight67)
            );
            outputInstance.setLightCoords(
                remap.vert3, LightCoordsUtil.smoothWeightedBlend(tc1, tc2, tc3, tc4, vert3weight01, vert3weight23, vert3weight45, vert3weight67)
            );
        } else {
            float lightLevel1 = (shade3 + shade0 + shadeCorner03 + shadeCenter) * 0.25F;
            float lightLevel2 = (shade2 + shade0 + shadeCorner02 + shadeCenter) * 0.25F;
            float lightLevel3 = (shade2 + shade1 + shadeCorner12 + shadeCenter) * 0.25F;
            float lightLevel4 = (shade3 + shade1 + shadeCorner13 + shadeCenter) * 0.25F;
            outputInstance.setLightCoords(remap.vert0, LightCoordsUtil.smoothBlend(light3, light0, lightCorner03, lightCenter));
            outputInstance.setLightCoords(remap.vert1, LightCoordsUtil.smoothBlend(light2, light0, lightCorner02, lightCenter));
            outputInstance.setLightCoords(remap.vert2, LightCoordsUtil.smoothBlend(light2, light1, lightCorner12, lightCenter));
            outputInstance.setLightCoords(remap.vert3, LightCoordsUtil.smoothBlend(light3, light1, lightCorner13, lightCenter));
            outputInstance.setColor(remap.vert0, ARGB.gray(lightLevel1));
            outputInstance.setColor(remap.vert1, ARGB.gray(lightLevel2));
            outputInstance.setColor(remap.vert2, ARGB.gray(lightLevel3));
            outputInstance.setColor(remap.vert3, ARGB.gray(lightLevel4));
        }

        CardinalLighting cardinalLighting = level.cardinalLighting();
        outputInstance.scaleColor(quad.materialInfo().shade() ? cardinalLighting.byFace(direction) : cardinalLighting.up());
    }

    public void prepareQuadFlat(
        BlockAndTintGetter level, BlockState state, BlockPos pos, int lightCoords, BakedQuad quad, QuadInstance outputInstance
    ) {
        if (lightCoords == CHECK_LIGHT) {
            this.prepareQuadShape(level, state, pos, quad, false);
            BlockPos lightPos = this.faceCubic ? this.scratchPos.setWithOffset(pos, quad.direction()) : pos;
            outputInstance.setLightCoords(this.cache.getLightCoords(state, level, lightPos));
        } else {
            outputInstance.setLightCoords(lightCoords);
        }

        CardinalLighting cardinalLighting = level.cardinalLighting();
        float directionalBrightness = quad.materialInfo().shade() ? cardinalLighting.byFace(quad.direction()) : cardinalLighting.up();
        outputInstance.setColor(ARGB.gray(directionalBrightness));
    }

    private void prepareQuadShape(BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, boolean ambientOcclusion) {
        float minX = 32.0F;
        float minY = 32.0F;
        float minZ = 32.0F;
        float maxX = -32.0F;
        float maxY = -32.0F;
        float maxZ = -32.0F;

        for (int i = 0; i < 4; i++) {
            Vector3fc position = quad.position(i);
            float x = position.x();
            float y = position.y();
            float z = position.z();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        if (ambientOcclusion) {
            this.faceShape[SizeInformation.WEST.index] = minX;
            this.faceShape[SizeInformation.EAST.index] = maxX;
            this.faceShape[SizeInformation.DOWN.index] = minY;
            this.faceShape[SizeInformation.UP.index] = maxY;
            this.faceShape[SizeInformation.NORTH.index] = minZ;
            this.faceShape[SizeInformation.SOUTH.index] = maxZ;
            this.faceShape[SizeInformation.FLIP_WEST.index] = 1.0F - minX;
            this.faceShape[SizeInformation.FLIP_EAST.index] = 1.0F - maxX;
            this.faceShape[SizeInformation.FLIP_DOWN.index] = 1.0F - minY;
            this.faceShape[SizeInformation.FLIP_UP.index] = 1.0F - maxY;
            this.faceShape[SizeInformation.FLIP_NORTH.index] = 1.0F - minZ;
            this.faceShape[SizeInformation.FLIP_SOUTH.index] = 1.0F - maxZ;
        }

        this.facePartial = switch (quad.direction()) {
            case DOWN, UP -> minX >= 1.0E-4F || minZ >= 1.0E-4F || maxX <= 0.9999F || maxZ <= 0.9999F;
            case NORTH, SOUTH -> minX >= 1.0E-4F || minY >= 1.0E-4F || maxX <= 0.9999F || maxY <= 0.9999F;
            case WEST, EAST -> minY >= 1.0E-4F || minZ >= 1.0E-4F || maxY <= 0.9999F || maxZ <= 0.9999F;
            default -> throw new MatchException(null, null);
        };

        this.faceCubic = switch (quad.direction()) {
            case DOWN -> minY == maxY && (minY < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos));
            case UP -> minY == maxY && (maxY > 0.9999F || state.isCollisionShapeFullBlock(level, pos));
            case NORTH -> minZ == maxZ && (minZ < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos));
            case SOUTH -> minZ == maxZ && (maxZ > 0.9999F || state.isCollisionShapeFullBlock(level, pos));
            case WEST -> minX == maxX && (minX < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos));
            case EAST -> minX == maxX && (maxX > 0.9999F || state.isCollisionShapeFullBlock(level, pos));
            default -> throw new MatchException(null, null);
        };
    }

    private enum AdjacencyInfo {
        DOWN(
            new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
            0.5F,
            true,
            new SizeInformation[]{
                SizeInformation.FLIP_WEST,
                SizeInformation.SOUTH,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.WEST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.WEST,
                SizeInformation.SOUTH
            },
            new SizeInformation[]{
                SizeInformation.FLIP_WEST,
                SizeInformation.NORTH,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.WEST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.WEST,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.FLIP_EAST,
                SizeInformation.NORTH,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.EAST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.EAST,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.FLIP_EAST,
                SizeInformation.SOUTH,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.EAST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.EAST,
                SizeInformation.SOUTH
            }
        ),
        UP(
            new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH},
            1.0F,
            true,
            new SizeInformation[]{
                SizeInformation.EAST,
                SizeInformation.SOUTH,
                SizeInformation.EAST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_EAST,
                SizeInformation.SOUTH
            },
            new SizeInformation[]{
                SizeInformation.EAST,
                SizeInformation.NORTH,
                SizeInformation.EAST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_EAST,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.WEST,
                SizeInformation.NORTH,
                SizeInformation.WEST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_WEST,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.WEST,
                SizeInformation.SOUTH,
                SizeInformation.WEST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_WEST,
                SizeInformation.SOUTH
            }
        ),
        NORTH(
            new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST},
            0.8F,
            true,
            new SizeInformation[]{
                SizeInformation.UP,
                SizeInformation.FLIP_WEST,
                SizeInformation.UP,
                SizeInformation.WEST,
                SizeInformation.FLIP_UP,
                SizeInformation.WEST,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_WEST
            },
            new SizeInformation[]{
                SizeInformation.UP,
                SizeInformation.FLIP_EAST,
                SizeInformation.UP,
                SizeInformation.EAST,
                SizeInformation.FLIP_UP,
                SizeInformation.EAST,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_EAST
            },
            new SizeInformation[]{
                SizeInformation.DOWN,
                SizeInformation.FLIP_EAST,
                SizeInformation.DOWN,
                SizeInformation.EAST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.EAST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_EAST
            },
            new SizeInformation[]{
                SizeInformation.DOWN,
                SizeInformation.FLIP_WEST,
                SizeInformation.DOWN,
                SizeInformation.WEST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.WEST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_WEST
            }
        ),
        SOUTH(
            new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
            0.8F,
            true,
            new SizeInformation[]{
                SizeInformation.UP,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_UP,
                SizeInformation.WEST,
                SizeInformation.UP,
                SizeInformation.WEST
            },
            new SizeInformation[]{
                SizeInformation.DOWN,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_WEST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.WEST,
                SizeInformation.DOWN,
                SizeInformation.WEST
            },
            new SizeInformation[]{
                SizeInformation.DOWN,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_DOWN,
                SizeInformation.EAST,
                SizeInformation.DOWN,
                SizeInformation.EAST
            },
            new SizeInformation[]{
                SizeInformation.UP,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_EAST,
                SizeInformation.FLIP_UP,
                SizeInformation.EAST,
                SizeInformation.UP,
                SizeInformation.EAST
            }
        ),
        WEST(
            new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH},
            0.6F,
            true,
            new SizeInformation[]{
                SizeInformation.UP,
                SizeInformation.SOUTH,
                SizeInformation.UP,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_UP,
                SizeInformation.SOUTH
            },
            new SizeInformation[]{
                SizeInformation.UP,
                SizeInformation.NORTH,
                SizeInformation.UP,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_UP,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.DOWN,
                SizeInformation.NORTH,
                SizeInformation.DOWN,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_NORTH,
                SizeInformation.FLIP_DOWN,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.DOWN,
                SizeInformation.SOUTH,
                SizeInformation.DOWN,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.FLIP_DOWN,
                SizeInformation.SOUTH
            }
        ),
        EAST(
            new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
            0.6F,
            true,
            new SizeInformation[]{
                SizeInformation.FLIP_DOWN,
                SizeInformation.SOUTH,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.DOWN,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.DOWN,
                SizeInformation.SOUTH
            },
            new SizeInformation[]{
                SizeInformation.FLIP_DOWN,
                SizeInformation.NORTH,
                SizeInformation.FLIP_DOWN,
                SizeInformation.FLIP_NORTH,
                SizeInformation.DOWN,
                SizeInformation.FLIP_NORTH,
                SizeInformation.DOWN,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.FLIP_UP,
                SizeInformation.NORTH,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_NORTH,
                SizeInformation.UP,
                SizeInformation.FLIP_NORTH,
                SizeInformation.UP,
                SizeInformation.NORTH
            },
            new SizeInformation[]{
                SizeInformation.FLIP_UP,
                SizeInformation.SOUTH,
                SizeInformation.FLIP_UP,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.UP,
                SizeInformation.FLIP_SOUTH,
                SizeInformation.UP,
                SizeInformation.SOUTH
            }
        );

        private final Direction[] corners;
        private final boolean doNonCubicWeight;
        private final SizeInformation[] vert0Weights;
        private final SizeInformation[] vert1Weights;
        private final SizeInformation[] vert2Weights;
        private final SizeInformation[] vert3Weights;
        private static final AdjacencyInfo[] BY_FACING = Util.make(new AdjacencyInfo[6], map -> {
            map[Direction.DOWN.get3DDataValue()] = DOWN;
            map[Direction.UP.get3DDataValue()] = UP;
            map[Direction.NORTH.get3DDataValue()] = NORTH;
            map[Direction.SOUTH.get3DDataValue()] = SOUTH;
            map[Direction.WEST.get3DDataValue()] = WEST;
            map[Direction.EAST.get3DDataValue()] = EAST;
        });

        AdjacencyInfo(
            Direction[] corners,
            float shadeWeight,
            boolean doNonCubicWeight,
            SizeInformation[] vert0Weights,
            SizeInformation[] vert1Weights,
            SizeInformation[] vert2Weights,
            SizeInformation[] vert3Weights
        ) {
            this.corners = corners;
            this.doNonCubicWeight = doNonCubicWeight;
            this.vert0Weights = vert0Weights;
            this.vert1Weights = vert1Weights;
            this.vert2Weights = vert2Weights;
            this.vert3Weights = vert3Weights;
        }

        public static AdjacencyInfo fromFacing(Direction direction) {
            return BY_FACING[direction.get3DDataValue()];
        }
    }

    private enum AmbientVertexRemap {
        DOWN(0, 1, 2, 3),
        UP(2, 3, 0, 1),
        NORTH(3, 0, 1, 2),
        SOUTH(0, 1, 2, 3),
        WEST(3, 0, 1, 2),
        EAST(1, 2, 3, 0);

        private final int vert0;
        private final int vert1;
        private final int vert2;
        private final int vert3;
        private static final AmbientVertexRemap[] BY_FACING = Util.make(new AmbientVertexRemap[6], map -> {
            map[Direction.DOWN.get3DDataValue()] = DOWN;
            map[Direction.UP.get3DDataValue()] = UP;
            map[Direction.NORTH.get3DDataValue()] = NORTH;
            map[Direction.SOUTH.get3DDataValue()] = SOUTH;
            map[Direction.WEST.get3DDataValue()] = WEST;
            map[Direction.EAST.get3DDataValue()] = EAST;
        });

        AmbientVertexRemap(int vert0, int vert1, int vert2, int vert3) {
            this.vert0 = vert0;
            this.vert1 = vert1;
            this.vert2 = vert2;
            this.vert3 = vert3;
        }

        public static AmbientVertexRemap fromFacing(Direction direction) {
            return BY_FACING[direction.get3DDataValue()];
        }
    }

    private enum SizeInformation {
        DOWN(0),
        UP(1),
        NORTH(2),
        SOUTH(3),
        WEST(4),
        EAST(5),
        FLIP_DOWN(6),
        FLIP_UP(7),
        FLIP_NORTH(8),
        FLIP_SOUTH(9),
        FLIP_WEST(10),
        FLIP_EAST(11);

        public static final int COUNT = values().length;
        private final int index;

        SizeInformation(int index) {
            this.index = index;
        }
    }
}
