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
import net.minecraft.core.BlockPos.MutableBlockPos;
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

public final class XenoFluidRenderer {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;

    private final FluidStateModelSet fluidModels;
    private final XenoLightDataCache lightCache;
    private final XenoOcclusionCache occlusionCache = new XenoOcclusionCache();

    private final MutableBlockPos scratchDown = new MutableBlockPos();
    private final MutableBlockPos scratchUp = new MutableBlockPos();
    private final MutableBlockPos scratchNorth = new MutableBlockPos();
    private final MutableBlockPos scratchSouth = new MutableBlockPos();
    private final MutableBlockPos scratchWest = new MutableBlockPos();
    private final MutableBlockPos scratchEast = new MutableBlockPos();
    private final MutableBlockPos scratchCorner = new MutableBlockPos();
    private final MutableBlockPos scratchLight = new MutableBlockPos();

    public XenoFluidRenderer(FluidStateModelSet fluidModels) {
        this(fluidModels, XenoLightDataCache.get());
    }

    public XenoFluidRenderer(FluidStateModelSet fluidModels, XenoLightDataCache lightCache) {
        this.fluidModels = fluidModels;
        this.lightCache = lightCache;
    }

    private boolean isFaceOccludedByState(Direction direction, float height, BlockState state) {
        if (!state.canOcclude()) {
            return false;
        }
        VoxelShape occluder = state.getFaceOcclusionShape(direction.getOpposite());
        if (XenoOcclusionCache.isEmpty(occluder)) {
            return false;
        }
        if (XenoOcclusionCache.isFullCube(occluder)) {
            return direction != Direction.UP || height == 1.0F;
        }
        VoxelShape shape = height >= 1.0F ? Shapes.block() : Shapes.box(0.0, 0.0, 0.0, 1.0, height, 1.0);
        return this.occlusionCache.occludes(shape, occluder, direction);
    }

    private boolean isFaceOccludedBySelf(BlockState state, Direction direction) {
        return this.isFaceOccludedByState(direction.getOpposite(), 1.0F, state);
    }

    public boolean shouldRenderFace(FluidState fluidState, BlockState blockState, Direction direction, FluidState neighborFluidState) {
        return !neighborFluidState.getType().isSame(fluidState.getType()) && !this.isFaceOccludedBySelf(blockState, direction);
    }

    public void tesselate(
            BlockAndTintGetter level,
            BlockPos pos,
            Output output,
            BlockState blockState,
            FluidState fluidState
    ) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        this.scratchDown.set(x, y - 1, z);
        BlockState blockStateDown = this.getState(level, this.scratchDown);
        FluidState fluidStateDown = blockStateDown.getFluidState();

        this.scratchUp.set(x, y + 1, z);
        BlockState blockStateUp = this.getState(level, this.scratchUp);
        FluidState fluidStateUp = blockStateUp.getFluidState();

        this.scratchNorth.set(x, y, z - 1);
        BlockState blockStateNorth = this.getState(level, this.scratchNorth);
        FluidState fluidStateNorth = blockStateNorth.getFluidState();

        this.scratchSouth.set(x, y, z + 1);
        BlockState blockStateSouth = this.getState(level, this.scratchSouth);
        FluidState fluidStateSouth = blockStateSouth.getFluidState();

        this.scratchWest.set(x - 1, y, z);
        BlockState blockStateWest = this.getState(level, this.scratchWest);
        FluidState fluidStateWest = blockStateWest.getFluidState();

        this.scratchEast.set(x + 1, y, z);
        BlockState blockStateEast = this.getState(level, this.scratchEast);
        FluidState fluidStateEast = blockStateEast.getFluidState();

        boolean renderUp = !fluidStateUp.getType().isSame(fluidState.getType());
        boolean renderDown = this.shouldRenderFace(fluidState, blockState, Direction.DOWN, fluidStateDown)
                && !this.isFaceOccludedByState(Direction.DOWN, MAX_FLUID_HEIGHT, blockStateDown);
        boolean renderNorth = this.shouldRenderFace(fluidState, blockState, Direction.NORTH, fluidStateNorth);
        boolean renderSouth = this.shouldRenderFace(fluidState, blockState, Direction.SOUTH, fluidStateSouth);
        boolean renderWest = this.shouldRenderFace(fluidState, blockState, Direction.WEST, fluidStateWest);
        boolean renderEast = this.shouldRenderFace(fluidState, blockState, Direction.EAST, fluidStateEast);

        if (!renderUp && !renderDown && !renderEast && !renderWest && !renderNorth && !renderSouth) {
            return;
        }

        FluidModel model = this.fluidModels.get(fluidState);
        XenoSectionLayerBuffer builder = output.getBuffer(model.layer());
        int tintColor = model.tintSource() != null ? model.tintSource().colorInWorld(blockState, level, pos) : -1;
        CardinalLighting cardinalLighting = level.cardinalLighting();
        Fluid type = fluidState.getType();

        float heightSelf = this.getHeight(level, type, pos, blockState, fluidState);
        float heightNorthEast, heightNorthWest, heightSouthEast, heightSouthWest;

        if (heightSelf >= 1.0F) {
            heightNorthEast = 1.0F;
            heightNorthWest = 1.0F;
            heightSouthEast = 1.0F;
            heightSouthWest = 1.0F;
        } else {
            float heightNorth = this.getHeight(level, type, this.scratchNorth, blockStateNorth, fluidStateNorth);
            float heightSouth = this.getHeight(level, type, this.scratchSouth, blockStateSouth, fluidStateSouth);
            float heightEast = this.getHeight(level, type, this.scratchEast, blockStateEast, fluidStateEast);
            float heightWest = this.getHeight(level, type, this.scratchWest, blockStateWest, fluidStateWest);

            heightNorthEast = this.calculateAverageHeight(level, type, heightSelf, heightNorth, heightEast, x + 1, y, z - 1);
            heightNorthWest = this.calculateAverageHeight(level, type, heightSelf, heightNorth, heightWest, x - 1, y, z - 1);
            heightSouthEast = this.calculateAverageHeight(level, type, heightSelf, heightSouth, heightEast, x + 1, y, z + 1);
            heightSouthWest = this.calculateAverageHeight(level, type, heightSelf, heightSouth, heightWest, x - 1, y, z + 1);
        }

        float localX = (float) (x & 15);
        float localY = (float) (y & 15);
        float localZ = (float) (z & 15);
        float bottomOffs = renderDown ? 0.001F : 0.0F;

        if (renderUp && !this.isFaceOccludedByState(
                Direction.UP, Math.min(Math.min(heightNorthWest, heightSouthWest), Math.min(heightSouthEast, heightNorthEast)), blockStateUp
        )) {
            heightNorthWest -= 0.001F;
            heightSouthWest -= 0.001F;
            heightSouthEast -= 0.001F;
            heightNorthEast -= 0.001F;

            Vec3 flow = fluidState.getFlow(level, pos);
            float u00, u01, u10, u11, v00, v01, v10, v11;

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

            int lightNW = this.getCornerLight(level, x, y, z);
            int lightSW = this.getCornerLight(level, x, y, z + 1);
            int lightSE = this.getCornerLight(level, x + 1, y, z + 1);
            int lightNE = this.getCornerLight(level, x + 1, y, z);

            int topColor = ARGB.scaleRGB(tintColor, cardinalLighting.up());
            boolean backwardFace = fluidState.shouldRenderBackwardUpFace(level, this.scratchUp);

            builder.writeQuad(
                    localX + 0.0F, localY + heightNorthWest, localZ + 0.0F, u00, v00,
                    localX + 0.0F, localY + heightSouthWest, localZ + 1.0F, u01, v01,
                    localX + 1.0F, localY + heightSouthEast, localZ + 1.0F, u10, v10,
                    localX + 1.0F, localY + heightNorthEast, localZ + 0.0F, u11, v11,
                    topColor, lightNW, lightSW, lightSE, lightNE, backwardFace
            );
        }

        if (renderDown) {
            TextureAtlasSprite stillSprite = model.stillMaterial().sprite();
            float u0 = stillSprite.getU0();
            float u1 = stillSprite.getU1();
            float v0 = stillSprite.getV0();
            float v1 = stillSprite.getV1();

            int belowLight = this.getLightCoords(level, this.scratchDown);
            int belowColor = ARGB.scaleRGB(tintColor, cardinalLighting.down());

            builder.writeQuad(
                    localX, localY + bottomOffs, localZ, u0, v0,
                    localX + 1.0F, localY + bottomOffs, localZ, u1, v0,
                    localX + 1.0F, localY + bottomOffs, localZ + 1.0F, u1, v1,
                    localX, localY + bottomOffs, localZ + 1.0F, u0, v1,
                    belowColor, belowLight, false
            );
        }

        int sideLight = this.getLightCoords(level, pos);

        for (Direction faceDir : Plane.HORIZONTAL) {
            float hh0, hh1, x0, z0, x1, z1;
            boolean renderCondition;
            BlockState faceState;

            switch (faceDir) {
                case NORTH -> {
                    hh0 = heightNorthWest;
                    hh1 = heightNorthEast;
                    x0 = localX;
                    x1 = localX + 1.0F;
                    z0 = localZ + 0.001F;
                    z1 = localZ + 0.001F;
                    renderCondition = renderNorth;
                    faceState = blockStateNorth;
                }
                case SOUTH -> {
                    hh0 = heightSouthEast;
                    hh1 = heightSouthWest;
                    x0 = localX + 1.0F;
                    x1 = localX;
                    z0 = localZ + 1.0F - 0.001F;
                    z1 = localZ + 1.0F - 0.001F;
                    renderCondition = renderSouth;
                    faceState = blockStateSouth;
                }
                case WEST -> {
                    hh0 = heightSouthWest;
                    hh1 = heightNorthWest;
                    x0 = localX + 0.001F;
                    x1 = localX + 0.001F;
                    z0 = localZ + 1.0F;
                    z1 = localZ;
                    renderCondition = renderWest;
                    faceState = blockStateWest;
                }
                case EAST -> {
                    hh0 = heightNorthEast;
                    hh1 = heightSouthEast;
                    x0 = localX + 1.0F - 0.001F;
                    x1 = localX + 1.0F - 0.001F;
                    z0 = localZ;
                    z1 = localZ + 1.0F;
                    renderCondition = renderEast;
                    faceState = blockStateEast;
                }
                default -> throw new UnsupportedOperationException();
            }

            if (renderCondition && !this.isFaceOccludedByState(faceDir, Math.max(hh0, hh1), faceState)) {
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

                builder.writeQuad(
                        x0, localY + hh0, z0, u0, v01,
                        x1, localY + hh1, z1, u1, v02,
                        x1, localY + bottomOffs, z1, u1, v1,
                        x0, localY + bottomOffs, z0, u0, v1,
                        faceColor, sideLight, !isOverlay
                );
            }
        }
    }

    private float calculateAverageHeight(
            BlockAndTintGetter level, Fluid type, float heightSelf, float height2, float height1, int cornerX, int cornerY, int cornerZ
    ) {
        if (height1 >= 1.0F || height2 >= 1.0F) {
            return 1.0F;
        }

        float sum = 0.0F;
        float count = 0.0F;

        if (height1 > 0.0F || height2 > 0.0F) {
            this.scratchCorner.set(cornerX, cornerY, cornerZ);
            float heightCorner = this.getHeight(level, type, this.scratchCorner);
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
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluidType, BlockPos pos) {
        BlockState state = this.getState(level, pos);
        return this.getHeight(level, fluidType, pos, state, state.getFluidState());
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluidType, BlockPos pos, BlockState state, FluidState fluidState) {
        if (fluidType.isSame(fluidState.getType())) {
            this.scratchUp.setWithOffset(pos, Direction.UP);
            BlockState aboveState = this.getState(level, this.scratchUp);
            return fluidType.isSame(aboveState.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
        }
        return !state.isSolid() ? 0.0F : -1.0F;
    }

    private int getLightCoords(BlockAndTintGetter level, BlockPos pos) {
        this.scratchLight.setWithOffset(pos, Direction.UP);
        int lightSelf = this.lightCache.getLightCoords(this.getState(level, pos), level, pos);
        int lightAbove = this.lightCache.getLightCoords(this.getState(level, this.scratchLight), level, this.scratchLight);
        return LightCoordsUtil.max(lightSelf, lightAbove);
    }

    private int getCornerLight(BlockAndTintGetter level, int x, int y, int z) {
        this.scratchLight.set(x, y, z);
        return this.getLightCoords(level, this.scratchLight);
    }

    private BlockState getState(BlockAndTintGetter level, BlockPos pos) {
        return this.lightCache != null ? this.lightCache.getState(level, pos) : level.getBlockState(pos);
    }

    @FunctionalInterface
    public interface Output {
        XenoSectionLayerBuffer getBuffer(ChunkSectionLayer layer);
    }
}
