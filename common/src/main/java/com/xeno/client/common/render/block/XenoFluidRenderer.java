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

import com.xeno.client.common.render.chunk.XenoSectionLayerBuffer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public record XenoFluidRenderer(FluidStateModelSet fluidModels) {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;

    private static boolean isFaceOccludedByState(Direction direction, float height, BlockState state) {
        VoxelShape occluder = state.getFaceOcclusionShape(direction.getOpposite());
        if (occluder == Shapes.empty()) {
            return false;
        } else if (occluder == Shapes.block()) {
            boolean fullBlock = height == 1.0F;
            return direction != Direction.UP || fullBlock;
        } else {
            VoxelShape shape = Shapes.box(0.0, 0.0, 0.0, 1.0, height, 1.0);
            return Shapes.blockOccludes(shape, occluder, direction);
        }
    }

    private static boolean isFaceOccludedBySelf(BlockState state, Direction direction) {
        return isFaceOccludedByState(direction.getOpposite(), 1.0F, state);
    }

    public static boolean shouldRenderFace(
            FluidState fluidState, BlockState blockState, Direction direction, FluidState neighborFluidState
    ) {
        return !neighborFluidState.getType().isSame(fluidState.getType()) && !isFaceOccludedBySelf(blockState, direction);
    }

    public void tesselate(
            BlockAndTintGetter level,
            BlockPos pos,
            Output output,
            BlockState blockState,
            FluidState fluidState
    ) {
        BlockState blockStateDown = level.getBlockState(pos.relative(Direction.DOWN));
        FluidState fluidStateDown = blockStateDown.getFluidState();
        BlockState blockStateUp = level.getBlockState(pos.relative(Direction.UP));
        FluidState fluidStateUp = blockStateUp.getFluidState();
        BlockState blockStateNorth = level.getBlockState(pos.relative(Direction.NORTH));
        FluidState fluidStateNorth = blockStateNorth.getFluidState();
        BlockState blockStateSouth = level.getBlockState(pos.relative(Direction.SOUTH));
        FluidState fluidStateSouth = blockStateSouth.getFluidState();
        BlockState blockStateWest = level.getBlockState(pos.relative(Direction.WEST));
        FluidState fluidStateWest = blockStateWest.getFluidState();
        BlockState blockStateEast = level.getBlockState(pos.relative(Direction.EAST));
        FluidState fluidStateEast = blockStateEast.getFluidState();
        boolean renderUp = !fluidStateUp.getType().isSame(fluidState.getType());
        boolean renderDown = shouldRenderFace(fluidState, blockState, Direction.DOWN, fluidStateDown)
                && !isFaceOccludedByState(Direction.DOWN, MAX_FLUID_HEIGHT, blockStateDown);
        boolean renderNorth = shouldRenderFace(fluidState, blockState, Direction.NORTH, fluidStateNorth);
        boolean renderSouth = shouldRenderFace(fluidState, blockState, Direction.SOUTH, fluidStateSouth);
        boolean renderWest = shouldRenderFace(fluidState, blockState, Direction.WEST, fluidStateWest);
        boolean renderEast = shouldRenderFace(fluidState, blockState, Direction.EAST, fluidStateEast);
        if (renderUp || renderDown || renderEast || renderWest || renderNorth || renderSouth) {
            FluidModel model = this.fluidModels.get(fluidState);
            XenoSectionLayerBuffer builder = output.getBuffer(model.layer());
            int tintColor = model.tintSource() != null ? model.tintSource().colorInWorld(blockState, level, pos) : -1;
            CardinalLighting cardinalLighting = level.cardinalLighting();
            Fluid type = fluidState.getType();
            float heightSelf = this.getHeight(level, type, pos, blockState, fluidState);
            float heightNorthEast;
            float heightNorthWest;
            float heightSouthEast;
            float heightSouthWest;
            if (heightSelf >= 1.0F) {
                heightNorthEast = 1.0F;
                heightNorthWest = 1.0F;
                heightSouthEast = 1.0F;
                heightSouthWest = 1.0F;
            } else {
                float heightNorth = this.getHeight(level, type, pos.north(), blockStateNorth, fluidStateNorth);
                float heightSouth = this.getHeight(level, type, pos.south(), blockStateSouth, fluidStateSouth);
                float heightEast = this.getHeight(level, type, pos.east(), blockStateEast, fluidStateEast);
                float heightWest = this.getHeight(level, type, pos.west(), blockStateWest, fluidStateWest);
                heightNorthEast = this.calculateAverageHeight(
                        level, type, heightSelf, heightNorth, heightEast, pos.relative(Direction.NORTH).relative(Direction.EAST)
                );
                heightNorthWest = this.calculateAverageHeight(
                        level, type, heightSelf, heightNorth, heightWest, pos.relative(Direction.NORTH).relative(Direction.WEST)
                );
                heightSouthEast = this.calculateAverageHeight(
                        level, type, heightSelf, heightSouth, heightEast, pos.relative(Direction.SOUTH).relative(Direction.EAST)
                );
                heightSouthWest = this.calculateAverageHeight(
                        level, type, heightSelf, heightSouth, heightWest, pos.relative(Direction.SOUTH).relative(Direction.WEST)
                );
            }

            float x = (float) (pos.getX() & 15);
            float y = (float) (pos.getY() & 15);
            float z = (float) (pos.getZ() & 15);
            float bottomOffs = renderDown ? 0.001F : 0.0F;
            if (renderUp
                    && !isFaceOccludedByState(
                    Direction.UP, Math.min(Math.min(heightNorthWest, heightSouthWest), Math.min(heightSouthEast, heightNorthEast)), blockStateUp
            )) {
                heightNorthWest -= 0.001F;
                heightSouthWest -= 0.001F;
                heightSouthEast -= 0.001F;
                heightNorthEast -= 0.001F;
                Vec3 flow = fluidState.getFlow(level, pos);
                float u00;
                float u01;
                float u10;
                float u11;
                float v00;
                float v01;
                float v10;
                float v11;
                if (flow.x == 0.0 && flow.z == 0.0) {
                    TextureAtlasSprite stillSprite = model.stillMaterial().sprite();
                    u00 = stillSprite.getU0();
                    v00 = stillSprite.getV0();
                    u01 = u00;
                    v01 = stillSprite.getV1();
                    u10 = stillSprite.getU1();
                    v10 = v01;
                    u11 = u10;
                    v11 = v00;
                } else {
                    float angle = (float) Mth.atan2(flow.z, flow.x) - (float) (Math.PI / 2);
                    float s = Mth.sin(angle) * 0.25F;
                    float c = Mth.cos(angle) * 0.25F;
                    TextureAtlasSprite flowingSprite = model.flowingMaterial().sprite();
                    u00 = flowingSprite.getU(0.5F + (-c - s));
                    v00 = flowingSprite.getV(0.5F + (-c + s));
                    u01 = flowingSprite.getU(0.5F + (-c + s));
                    v01 = flowingSprite.getV(0.5F + (c + s));
                    u10 = flowingSprite.getU(0.5F + (c + s));
                    v10 = flowingSprite.getV(0.5F + (c - s));
                    u11 = flowingSprite.getU(0.5F + (c - s));
                    v11 = flowingSprite.getV(0.5F + (-c - s));
                }

                int topLightCoords = this.getLightCoords(level, pos);
                int topColor = ARGB.scaleRGB(tintColor, cardinalLighting.up());
                this.addFace(
                        builder,
                        x + 0.0F,
                        y + heightNorthWest,
                        z + 0.0F,
                        u00,
                        v00,
                        x + 0.0F,
                        y + heightSouthWest,
                        z + 1.0F,
                        u01,
                        v01,
                        x + 1.0F,
                        y + heightSouthEast,
                        z + 1.0F,
                        u10,
                        v10,
                        x + 1.0F,
                        y + heightNorthEast,
                        z + 0.0F,
                        u11,
                        v11,
                        topColor,
                        topLightCoords,
                        fluidState.shouldRenderBackwardUpFace(level, pos.above())
                );
            }

            if (renderDown) {
                TextureAtlasSprite stillSprite = model.stillMaterial().sprite();
                float u0 = stillSprite.getU0();
                float u1 = stillSprite.getU1();
                float v0 = stillSprite.getV0();
                float v1 = stillSprite.getV1();
                int belowLightCoords = this.getLightCoords(level, pos.below());
                int belowColor = ARGB.scaleRGB(tintColor, cardinalLighting.down());
                this.addFace(
                        builder,
                        x,
                        y + bottomOffs,
                        z,
                        u0,
                        v0,
                        x + 1.0F,
                        y + bottomOffs,
                        z,
                        u1,
                        v0,
                        x + 1.0F,
                        y + bottomOffs,
                        z + 1.0F,
                        u1,
                        v1,
                        x,
                        y + bottomOffs,
                        z + 1.0F,
                        u0,
                        v1,
                        belowColor,
                        belowLightCoords,
                        false
                );
            }

            int sideLightCoords = this.getLightCoords(level, pos);

            for (Direction faceDir : Plane.HORIZONTAL) {
                float hh0;
                float hh1;
                float x0;
                float z0;
                float x1;
                float z1;
                boolean renderCondition;
                BlockState faceState;
                switch (faceDir) {
                    case NORTH:
                        hh0 = heightNorthWest;
                        hh1 = heightNorthEast;
                        x0 = x;
                        x1 = x + 1.0F;
                        z0 = z + 0.001F;
                        z1 = z + 0.001F;
                        renderCondition = renderNorth;
                        faceState = blockStateNorth;
                        break;
                    case SOUTH:
                        hh0 = heightSouthEast;
                        hh1 = heightSouthWest;
                        x0 = x + 1.0F;
                        x1 = x;
                        z0 = z + 1.0F - 0.001F;
                        z1 = z + 1.0F - 0.001F;
                        renderCondition = renderSouth;
                        faceState = blockStateSouth;
                        break;
                    case WEST:
                        hh0 = heightSouthWest;
                        hh1 = heightNorthWest;
                        x0 = x + 0.001F;
                        x1 = x + 0.001F;
                        z0 = z + 1.0F;
                        z1 = z;
                        renderCondition = renderWest;
                        faceState = blockStateWest;
                        break;
                    case EAST:
                        hh0 = heightNorthEast;
                        hh1 = heightSouthEast;
                        x0 = x + 1.0F - 0.001F;
                        x1 = x + 1.0F - 0.001F;
                        z0 = z;
                        z1 = z + 1.0F;
                        renderCondition = renderEast;
                        faceState = blockStateEast;
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }

                if (renderCondition && !isFaceOccludedByState(faceDir, Math.max(hh0, hh1), faceState)) {
                    TextureAtlasSprite sprite = model.flowingMaterial().sprite();
                    boolean isOverlay = false;
                    if (model.overlayMaterial() != null) {
                        Block relativeBlock = faceState.getBlock();
                        if (relativeBlock instanceof HalfTransparentBlock || relativeBlock instanceof LeavesBlock) {
                            sprite = model.overlayMaterial().sprite();
                            isOverlay = true;
                        }
                    }

                    float u0 = sprite.getU(0.0F);
                    float u1 = sprite.getU(0.5F);
                    float v01 = sprite.getV((1.0F - hh0) * 0.5F);
                    float v02 = sprite.getV((1.0F - hh1) * 0.5F);
                    float v1 = sprite.getV(0.5F);
                    float shadeSide = faceDir.getAxis() == Axis.Z ? cardinalLighting.north() : cardinalLighting.west();
                    int faceColor = ARGB.scaleRGB(tintColor, cardinalLighting.up() * shadeSide);
                    this.addFace(
                            builder,
                            x0,
                            y + hh0,
                            z0,
                            u0,
                            v01,
                            x1,
                            y + hh1,
                            z1,
                            u1,
                            v02,
                            x1,
                            y + bottomOffs,
                            z1,
                            u1,
                            v1,
                            x0,
                            y + bottomOffs,
                            z0,
                            u0,
                            v1,
                            faceColor,
                            sideLightCoords,
                            !isOverlay
                    );
                }
            }
        }
    }

    private void addFace(
            XenoSectionLayerBuffer builder,
            float x0,
            float y0,
            float z0,
            float u0,
            float v0,
            float x1,
            float y1,
            float z1,
            float u1,
            float v1,
            float x2,
            float y2,
            float z2,
            float u2,
            float v2,
            float x3,
            float y3,
            float z3,
            float u3,
            float v3,
            int color,
            int lightCoords,
            boolean addBackFace
    ) {
        builder.writeQuad(x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3, color, lightCoords, addBackFace);
    }

    private float calculateAverageHeight(
            BlockAndTintGetter level, Fluid type, float heightSelf, float height2, float height1, BlockPos cornerPos
    ) {
        if (!(height1 >= 1.0F) && !(height2 >= 1.0F)) {
            float sum = 0.0F;
            float count = 0.0F;
            if (height1 > 0.0F || height2 > 0.0F) {
                float heightCorner = this.getHeight(level, type, cornerPos);
                if (heightCorner >= 1.0F) {
                    return 1.0F;
                }

                if (heightCorner >= 0.8F) {
                    sum += heightCorner * 10.0F;
                    count += 10.0F;
                } else if (heightCorner >= 0.0F) {
                    sum += heightCorner;
                    count += 1.0F;
                }
            }

            if (heightSelf >= 0.8F) {
                sum += heightSelf * 10.0F;
                count += 10.0F;
            } else if (heightSelf >= 0.0F) {
                sum += heightSelf;
                count += 1.0F;
            }

            if (height1 >= 0.8F) {
                sum += height1 * 10.0F;
                count += 10.0F;
            } else if (height1 >= 0.0F) {
                sum += height1;
                count += 1.0F;
            }

            if (height2 >= 0.8F) {
                sum += height2 * 10.0F;
                count += 10.0F;
            } else if (height2 >= 0.0F) {
                sum += height2;
                count += 1.0F;
            }

            return count > 0.0F ? sum / count : 0.0F;
        } else {
            return 1.0F;
        }
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluidType, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return this.getHeight(level, fluidType, pos, state, state.getFluidState());
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluidType, BlockPos pos, BlockState state, FluidState fluidState) {
        if (fluidType.isSame(fluidState.getType())) {
            BlockState aboveState = level.getBlockState(pos.above());
            return fluidType.isSame(aboveState.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
        } else {
            return !state.isSolid() ? 0.0F : -1.0F;
        }
    }

    private int getLightCoords(BlockAndTintGetter level, BlockPos pos) {
        return LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level, pos), LightCoordsUtil.getLightCoords(level, pos.above()));
    }

    @FunctionalInterface
    public interface Output {
        XenoSectionLayerBuffer getBuffer(ChunkSectionLayer layer);
    }
}
