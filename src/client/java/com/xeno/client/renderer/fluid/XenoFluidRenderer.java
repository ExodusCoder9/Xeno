package com.xeno.client.renderer.fluid;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.xeno.client.renderer.light.XenoFlatLightPipeline;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.Hash;
import net.minecraft.world.phys.shapes.BooleanOp;

public class XenoFluidRenderer {

    private static final float EPSILON = 0.001f;
    private static final float DISCARD_SAMPLE = -1.0f;
    private static final float FULL_HEIGHT = 0.8888889f;
    private static final float[] AO_TABLE = new float[]{1.0F, 0.8F, 0.6F, 0.4F};

    // DFS bits
    private static final int CULL_NONE = 0;
    private static final int CULL_EXPOSED_OUT = 1;
    private static final int CULL_EXPOSED_BOTH = 2;

    private final FluidStateModelSet fluidModels;
    private final boolean smoothLighting;

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos cursorTwo = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

    private float sumHeights = 0.0f;
    private int countHeights = 0;

    private final GeometryOcclusionCache occlusionCache = new GeometryOcclusionCache();

    public XenoFluidRenderer(FluidStateModelSet fluidModels, boolean smoothLighting) {
        this.fluidModels = fluidModels;
        this.smoothLighting = smoothLighting;
    }

    private boolean isSideVisible(BlockAndTintGetter view, BlockPos selfPos, Direction facing, FluidState fluid) {
        BlockState neighbor = view.getBlockState(this.cursorTwo.setWithOffset(selfPos, facing));
        if (neighbor.getFluidState().getType().isSame(fluid.getType())) {
            return false;
        }
        if (facing == Direction.UP) {
            return true;
        }
        if (!neighbor.canOcclude()) {
            return true;
        }
        VoxelShape neighborShape = neighbor.getFaceOcclusionShape(facing.getOpposite());
        if (GeometryOcclusionCache.isEmpty(neighborShape)) {
            return true;
        }
        return !GeometryOcclusionCache.isFull(neighborShape);
    }

    private boolean canSeeSelf(BlockState selfBlockState, Direction facing, VoxelShape fluidShape) {
        if (selfBlockState.canOcclude()) {
            VoxelShape selfShape = selfBlockState.getFaceOcclusionShape(facing);
            if (!GeometryOcclusionCache.isEmpty(selfShape)) {
                if (GeometryOcclusionCache.isFull(selfShape) && GeometryOcclusionCache.isFull(fluidShape)) {
                    return false;
                }
                return !this.occlusionCache.check(fluidShape, selfShape);
            }
        }
        return true;
    }

    private boolean canSeeSelfFull(BlockState blockState, Direction dir) {
        return this.canSeeSelf(blockState, dir, Shapes.block());
    }

    private boolean isExposedToNeighbor(BlockState ownBlockState, BlockState neighborBlockState, Direction facing, float height) {
        if (height <= 0.0F) return false;
        if (!neighborBlockState.canOcclude()) return true;
        if (facing == Direction.UP && height < 1.0F) return true;

        VoxelShape neighborShape = neighborBlockState.getFaceOcclusionShape(facing.getOpposite());
        if (GeometryOcclusionCache.isEmpty(neighborShape)) return true;
        if (GeometryOcclusionCache.isFull(neighborShape)) return false;

        VoxelShape fluidBox = (height >= 1.0F) ? Shapes.block() : Shapes.box(0.0, 0.0, 0.0, 1.0, height, 1.0);
        VoxelShape ownShape = ownBlockState.getFaceOcclusionShape(facing);

        return !this.occlusionCache.check(fluidBox, neighborShape, ownShape);
    }

    private boolean isExposedOffset(BlockAndTintGetter world, BlockState ownBlockState, BlockPos originPos, Direction dir, float height) {
        return this.isExposedToNeighbor(ownBlockState, world.getBlockState(this.cursor.setWithOffset(originPos, dir)), dir, height);
    }

    private float getHeightAt(BlockAndTintGetter world, Fluid fluid, BlockPos blockPos) {
        BlockState blockState = world.getBlockState(blockPos);
        FluidState fluidState = blockState.getFluidState();

        if (fluid.isSame(fluidState.getType())) {
            FluidState fluidStateUp = world.getFluidState(this.cursor.setWithOffset(blockPos, Direction.UP));
            if (fluid.isSame(fluidStateUp.getType())) {
                return 1.0f;
            } else {
                return fluidState.getOwnHeight();
            }
        }
        return blockState.isSolidRender() ? DISCARD_SAMPLE : 0.0f;
    }

    private float getHeightAt(BlockAndTintGetter world, Fluid fluid, BlockPos origin, Direction offset) {
        return this.getHeightAt(world, fluid, this.cursor.setWithOffset(origin, offset));
    }

    private void pushHeight(float sample) {
        if (sample >= 0.8f) {
            this.sumHeights += sample * 10.0f;
            this.countHeights += 10;
        } else if (sample >= 0.0f) {
            this.sumHeights += sample;
            this.countHeights++;
        }
    }

    private float getCornerHeight(BlockAndTintGetter world, BlockPos origin, Fluid fluid, float baseHeight, Direction dirA, Direction dirB, float heightA, float heightB, boolean exposedA, boolean exposedB) {
        float fHeightA = exposedA ? heightA : DISCARD_SAMPLE;
        float fHeightB = exposedB ? heightB : DISCARD_SAMPLE;

        if (fHeightA >= 1.0f || fHeightB >= 1.0f) {
            return 1.0f;
        }

        boolean cornerExposed = false;
        if (fHeightA > 0.0f || fHeightB > 0.0f) {
            BlockPos nA = this.cursor.setWithOffset(origin, dirA);
            BlockState sA = world.getBlockState(nA);
            boolean pA = this.canSeeSelfFull(sA, dirB) && this.isExposedOffset(world, sA, nA, dirB, 1.0f);

            BlockPos nB = this.cursor.setWithOffset(origin, dirB);
            BlockState sB = world.getBlockState(nB);
            boolean pB = this.canSeeSelfFull(sB, dirA) && this.isExposedOffset(world, sB, nB, dirA, 1.0f);

            cornerExposed = pA && pB;
            if ((exposedA && pA) || (exposedB && pB)) {
                BlockPos diag = this.cursor.set(origin).move(dirA).move(dirB);
                float dh = this.getHeightAt(world, fluid, diag);
                if (dh >= 1.0f) return 1.0f;
                this.pushHeight(dh);
            }
        }

        if (exposedA || (exposedB && cornerExposed)) this.pushHeight(heightA);
        if (exposedB || (exposedA && cornerExposed)) this.pushHeight(heightB);

        if (this.countHeights > 0) {
            this.pushHeight(baseHeight);
            float result = this.sumHeights / this.countHeights;
            this.sumHeights = 0.0f;
            this.countHeights = 0;
            return result;
        }
        return baseHeight;
    }

    private int checkFloodedCave(BlockAndTintGetter level, BlockPos origin, FluidState fluid) {
        return this.searchCave(level, origin, fluid, 0, 0, 0L);
    }

    private int searchCave(BlockAndTintGetter level, BlockPos origin, FluidState fluid, int x, int z, long mask) {
        if (x > 1 || x < -1 || z > 1 || z < -1) return CULL_NONE;
        
        long bit = 1L << ((x + 2) + (z + 2) * 5);
        if ((mask & bit) != 0) return CULL_NONE;
        mask |= bit;

        BlockState neighbor = level.getBlockState(this.cursor.setWithOffset(origin, x, 0, z));
        if (neighbor.isSolidRender()) return CULL_NONE;

        Fluid type = fluid.getType();
        BlockState above = level.getBlockState(this.cursor.move(Direction.UP));
        boolean aboveIsFluid = above.getFluidState().isSourceOfType(type);

        int result = CULL_NONE;
        if (neighbor.getFluidState().isSourceOfType(type)) {
            if (!aboveIsFluid) {
                result |= this.searchCave(level, origin, fluid, x + 1, z, mask);
                if (result == CULL_EXPOSED_BOTH) return result;
                result |= this.searchCave(level, origin, fluid, x - 1, z, mask);
                if (result == CULL_EXPOSED_BOTH) return result;
                result |= this.searchCave(level, origin, fluid, x, z + 1, mask);
                if (result == CULL_EXPOSED_BOTH) return result;
                result |= this.searchCave(level, origin, fluid, x, z - 1, mask);
                if (result == CULL_EXPOSED_BOTH) return result;
            }
        } else {
            result = CULL_EXPOSED_OUT;
        }

        if (!aboveIsFluid && !above.isSolidRender()) {
            return CULL_EXPOSED_BOTH;
        }
        return result;
    }

    private int fetchBlock(int val) { return val & 0xFFFF; }
    private int fetchSky(int val) { return (val >> 16) & 0xFFFF; }
    private int buildLight(int block, int sky) { return block | (sky << 16); }

    private void compileLighting(BlockAndTintGetter level, BlockPos pos, Direction face, float[] outAo, int[] outLight) {
        int cx = pos.getX(), cy = pos.getY(), cz = pos.getZ();
        int fx = cx + face.getStepX(), fy = cy + face.getStepY(), fz = cz + face.getStepZ();
        
        float shade = XenoFlatLightPipeline.getFaceShade(face);
        int cl = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx, fy, fz));
        
        if (!this.smoothLighting) {
            outAo[0] = outAo[1] = outAo[2] = outAo[3] = shade;
            outLight[0] = outLight[1] = outLight[2] = outLight[3] = cl;
            return;
        }

        // To correctly compute the 4 corners, we define two unit vectors U and V on the plane of the face.
        int ux = 0, uy = 0, uz = 0;
        int vx = 0, vy = 0, vz = 0;
        
        switch (face.getAxis()) {
            case Y -> { ux = 1; vz = 1; }
            case Z -> { ux = 1; vy = 1; }
            case X -> { uz = 1; vy = 1; }
        }

        // We fetch the 8 neighbors
        // N1: -U
        // N2: +U
        // N3: -V
        // N4: +V
        // N5: -U, -V
        // N6: +U, -V
        // N7: -U, +V
        // N8: +U, +V
        
        boolean o_nU = level.getBlockState(this.lightPos.set(fx - ux, fy - uy, fz - uz)).isSolidRender();
        boolean o_pU = level.getBlockState(this.lightPos.set(fx + ux, fy + uy, fz + uz)).isSolidRender();
        boolean o_nV = level.getBlockState(this.lightPos.set(fx - vx, fy - vy, fz - vz)).isSolidRender();
        boolean o_pV = level.getBlockState(this.lightPos.set(fx + vx, fy + vy, fz + vz)).isSolidRender();
        
        boolean o_nUnV = level.getBlockState(this.lightPos.set(fx - ux - vx, fy - uy - vy, fz - uz - vz)).isSolidRender();
        boolean o_pUnV = level.getBlockState(this.lightPos.set(fx + ux - vx, fy + uy - vy, fz + uz - vz)).isSolidRender();
        boolean o_nUpV = level.getBlockState(this.lightPos.set(fx - ux + vx, fy - uy + vy, fz - uz + vz)).isSolidRender();
        boolean o_pUpV = level.getBlockState(this.lightPos.set(fx + ux + vx, fy + uy + vy, fz + uz + vz)).isSolidRender();

        int l_nU = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - ux, fy - uy, fz - uz));
        int l_pU = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + ux, fy + uy, fz + uz));
        int l_nV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - vx, fy - vy, fz - vz));
        int l_pV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + vx, fy + vy, fz + vz));
        
        int l_nUnV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - ux - vx, fy - uy - vy, fz - uz - vz));
        int l_pUnV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + ux - vx, fy + uy - vy, fz + uz - vz));
        int l_nUpV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - ux + vx, fy - uy + vy, fz - uz + vz));
        int l_pUpV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + ux + vx, fy + uy + vy, fz + uz + vz));

        // Corner computations
        // C(-U, -V)
        int occ_nUnV = (o_nU ? 1 : 0) + (o_nV ? 1 : 0) + ((o_nU && o_nV) ? 1 : (o_nUnV ? 1 : 0));
        int cb = fetchBlock(cl), cs = fetchSky(cl);
        int c_nUnV = buildLight((cb + fetchBlock(l_nU) + fetchBlock(l_nV) + fetchBlock(l_nUnV)) / 4, (cs + fetchSky(l_nU) + fetchSky(l_nV) + fetchSky(l_nUnV)) / 4);

        // C(+U, -V)
        int occ_pUnV = (o_pU ? 1 : 0) + (o_nV ? 1 : 0) + ((o_pU && o_nV) ? 1 : (o_pUnV ? 1 : 0));
        int c_pUnV = buildLight((cb + fetchBlock(l_pU) + fetchBlock(l_nV) + fetchBlock(l_pUnV)) / 4, (cs + fetchSky(l_pU) + fetchSky(l_nV) + fetchSky(l_pUnV)) / 4);

        // C(-U, +V)
        int occ_nUpV = (o_nU ? 1 : 0) + (o_pV ? 1 : 0) + ((o_nU && o_pV) ? 1 : (o_nUpV ? 1 : 0));
        int c_nUpV = buildLight((cb + fetchBlock(l_nU) + fetchBlock(l_pV) + fetchBlock(l_nUpV)) / 4, (cs + fetchSky(l_nU) + fetchSky(l_pV) + fetchSky(l_nUpV)) / 4);

        // C(+U, +V)
        int occ_pUpV = (o_pU ? 1 : 0) + (o_pV ? 1 : 0) + ((o_pU && o_pV) ? 1 : (o_pUpV ? 1 : 0));
        int c_pUpV = buildLight((cb + fetchBlock(l_pU) + fetchBlock(l_pV) + fetchBlock(l_pUpV)) / 4, (cs + fetchSky(l_pU) + fetchSky(l_pV) + fetchSky(l_pUpV)) / 4);

        // Map to standard output 0..3 based on the face
        switch (face) {
            case UP -> {
                outAo[0] = AO_TABLE[occ_nUnV] * shade; outLight[0] = c_nUnV;
                outAo[1] = AO_TABLE[occ_nUpV] * shade; outLight[1] = c_nUpV;
                outAo[2] = AO_TABLE[occ_pUpV] * shade; outLight[2] = c_pUpV;
                outAo[3] = AO_TABLE[occ_pUnV] * shade; outLight[3] = c_pUnV;
            }
            case DOWN -> {
                outAo[0] = AO_TABLE[occ_nUpV] * shade; outLight[0] = c_nUpV;
                outAo[1] = AO_TABLE[occ_nUnV] * shade; outLight[1] = c_nUnV;
                outAo[2] = AO_TABLE[occ_pUnV] * shade; outLight[2] = c_pUnV;
                outAo[3] = AO_TABLE[occ_pUpV] * shade; outLight[3] = c_pUpV;
            }
            case NORTH, EAST -> {
                outAo[0] = AO_TABLE[occ_nUnV] * shade; outLight[0] = c_nUnV;
                outAo[1] = AO_TABLE[occ_pUnV] * shade; outLight[1] = c_pUnV;
                outAo[2] = AO_TABLE[occ_pUpV] * shade; outLight[2] = c_pUpV;
                outAo[3] = AO_TABLE[occ_nUpV] * shade; outLight[3] = c_nUpV;
            }
            case SOUTH, WEST -> {
                outAo[0] = AO_TABLE[occ_pUnV] * shade; outLight[0] = c_pUnV;
                outAo[1] = AO_TABLE[occ_nUnV] * shade; outLight[1] = c_nUnV;
                outAo[2] = AO_TABLE[occ_nUpV] * shade; outLight[2] = c_nUpV;
                outAo[3] = AO_TABLE[occ_pUpV] * shade; outLight[3] = c_pUpV;
            }
        }
    }

    public void tesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState blockState, FluidState fluidState) {
        Fluid fluid = fluidState.getType();

        boolean canSeeUp = this.canSeeSelfFull(blockState, Direction.UP) && this.isSideVisible(level, pos, Direction.UP, fluidState);
        boolean canSeeDown = this.canSeeSelfFull(blockState, Direction.DOWN) && this.isSideVisible(level, pos, Direction.DOWN, fluidState) && this.isExposedOffset(level, blockState, pos, Direction.DOWN, FULL_HEIGHT);

        boolean selfNorth = this.canSeeSelfFull(blockState, Direction.NORTH);
        boolean selfSouth = this.canSeeSelfFull(blockState, Direction.SOUTH);
        boolean selfWest = this.canSeeSelfFull(blockState, Direction.WEST);
        boolean selfEast = this.canSeeSelfFull(blockState, Direction.EAST);

        boolean canSeeNorth = selfNorth && this.isSideVisible(level, pos, Direction.NORTH, fluidState);
        boolean canSeeSouth = selfSouth && this.isSideVisible(level, pos, Direction.SOUTH, fluidState);
        boolean canSeeWest = selfWest && this.isSideVisible(level, pos, Direction.WEST, fluidState);
        boolean canSeeEast = selfEast && this.isSideVisible(level, pos, Direction.EAST, fluidState);

        if (!canSeeUp && !canSeeDown && !canSeeEast && !canSeeWest && !canSeeNorth && !canSeeSouth) {
            return;
        }

        float baseHeight = this.getHeightAt(level, fluid, pos);
        float hNW, hSW, hSE, hNE;

        if (baseHeight >= 1.0f) {
            hNW = hSW = hSE = hNE = 1.0f;
        } else {
            boolean exN = selfNorth && this.isExposedOffset(level, blockState, pos, Direction.NORTH, 1.0f);
            boolean exS = selfSouth && this.isExposedOffset(level, blockState, pos, Direction.SOUTH, 1.0f);
            boolean exW = selfWest && this.isExposedOffset(level, blockState, pos, Direction.WEST, 1.0f);
            boolean exE = selfEast && this.isExposedOffset(level, blockState, pos, Direction.EAST, 1.0f);

            float dhN = this.getHeightAt(level, fluid, pos, Direction.NORTH);
            float dhS = this.getHeightAt(level, fluid, pos, Direction.SOUTH);
            float dhE = this.getHeightAt(level, fluid, pos, Direction.EAST);
            float dhW = this.getHeightAt(level, fluid, pos, Direction.WEST);

            hNW = this.getCornerHeight(level, pos, fluid, baseHeight, Direction.NORTH, Direction.WEST, dhN, dhW, exN, exW);
            hSW = this.getCornerHeight(level, pos, fluid, baseHeight, Direction.SOUTH, Direction.WEST, dhS, dhW, exS, exW);
            hSE = this.getCornerHeight(level, pos, fluid, baseHeight, Direction.SOUTH, Direction.EAST, dhS, dhE, exS, exE);
            hNE = this.getCornerHeight(level, pos, fluid, baseHeight, Direction.NORTH, Direction.EAST, dhN, dhE, exN, exE);

            canSeeNorth &= exN; canSeeSouth &= exS; canSeeWest &= exW; canSeeEast &= exE;
        }

        FluidModel model = this.fluidModels.get(fluidState);
        VertexConsumer builder = output.getBuilder(model.layer());

        int color = (model.tintSource() != null) ? model.tintSource().colorInWorld(blockState, level, pos) : -1;

        float bx = pos.getX() & 15, by = pos.getY() & 15, bz = pos.getZ() & 15;
        float[] ao = new float[4];
        int[] li = new int[4];
        
        boolean renderInwardUp = false;

        if (canSeeUp) {
            float lowest = Math.min(Math.min(hNW, hSW), Math.min(hSE, hNE));
            canSeeUp = this.isExposedOffset(level, blockState, pos, Direction.UP, lowest);
            renderInwardUp = canSeeUp;

            if (canSeeUp && fluidState.isSource()) {
                int status = this.checkFloodedCave(level, pos, fluidState);
                canSeeUp = (status != CULL_NONE);
                renderInwardUp = (status == CULL_EXPOSED_BOTH);
            }
        }

        if (canSeeUp) {
            hNW -= EPSILON; hSW -= EPSILON; hSE -= EPSILON; hNE -= EPSILON;
            Vec3 vel = fluidState.getFlow(level, pos);
            TextureAtlasSprite sp;
            float u1, u2, u3, u4, v1, v2, v3, v4;

            if (vel.x == 0.0D && vel.z == 0.0D) {
                sp = model.stillMaterial().sprite();
                u1 = u2 = sp.getU0(); u3 = u4 = sp.getU1();
                v1 = v4 = sp.getV0(); v2 = v3 = sp.getV1();
            } else {
                sp = model.flowingMaterial().sprite();
                float angle = (float) Mth.atan2(vel.z, vel.x) - 1.5707964f;
                float sin = Mth.sin(angle) * 0.25F, cos = Mth.cos(angle) * 0.25F;
                u1 = sp.getU(0.5F - cos - sin); v1 = sp.getV(0.5F - cos + sin);
                u2 = sp.getU(0.5F - cos + sin); v2 = sp.getV(0.5F + cos + sin);
                u3 = sp.getU(0.5F + cos + sin); v3 = sp.getV(0.5F + cos - sin);
                u4 = sp.getU(0.5F + cos - sin); v4 = sp.getV(0.5F - cos - sin);
            }

            this.compileLighting(level, pos, Direction.UP, ao, li);
            boolean flat = Math.abs(hNE - hNW) <= 0.011f && Math.abs(hNW - hSE) <= 0.011f && Math.abs(hSE - hSW) <= 0.011f && Math.abs(hSW - hNE) <= 0.011f;
            boolean turn = flat || (hNE > hNW && hNE > hSE) || (hNE < hNW && hNE < hSE) || (hSW > hNW && hSW > hSE) || (hSW < hNW && hSW < hSE);

            if (turn) {
                pushV(builder, bx + 1.0f, by + hNE, bz + 0.0f, color, u4, v4, 0, 1, 0, li[3], ao[3]);
                pushV(builder, bx + 0.0f, by + hNW, bz + 0.0f, color, u1, v1, 0, 1, 0, li[0], ao[0]);
                pushV(builder, bx + 0.0f, by + hSW, bz + 1.0f, color, u2, v2, 0, 1, 0, li[1], ao[1]);
                pushV(builder, bx + 1.0f, by + hSE, bz + 1.0f, color, u3, v3, 0, 1, 0, li[2], ao[2]);
            } else {
                pushV(builder, bx + 0.0f, by + hNW, bz + 0.0f, color, u1, v1, 0, 1, 0, li[0], ao[0]);
                pushV(builder, bx + 0.0f, by + hSW, bz + 1.0f, color, u2, v2, 0, 1, 0, li[1], ao[1]);
                pushV(builder, bx + 1.0f, by + hSE, bz + 1.0f, color, u3, v3, 0, 1, 0, li[2], ao[2]);
                pushV(builder, bx + 1.0f, by + hNE, bz + 0.0f, color, u4, v4, 0, 1, 0, li[3], ao[3]);
            }
            
            if (renderInwardUp) {
                if (turn) {
                    pushV(builder, bx + 1.0f, by + hSE, bz + 1.0f, color, u3, v3, 0, -1, 0, li[2], ao[2]);
                    pushV(builder, bx + 0.0f, by + hSW, bz + 1.0f, color, u2, v2, 0, -1, 0, li[1], ao[1]);
                    pushV(builder, bx + 0.0f, by + hNW, bz + 0.0f, color, u1, v1, 0, -1, 0, li[0], ao[0]);
                    pushV(builder, bx + 1.0f, by + hNE, bz + 0.0f, color, u4, v4, 0, -1, 0, li[3], ao[3]);
                } else {
                    pushV(builder, bx + 1.0f, by + hNE, bz + 0.0f, color, u4, v4, 0, -1, 0, li[3], ao[3]);
                    pushV(builder, bx + 1.0f, by + hSE, bz + 1.0f, color, u3, v3, 0, -1, 0, li[2], ao[2]);
                    pushV(builder, bx + 0.0f, by + hSW, bz + 1.0f, color, u2, v2, 0, -1, 0, li[1], ao[1]);
                    pushV(builder, bx + 0.0f, by + hNW, bz + 0.0f, color, u1, v1, 0, -1, 0, li[0], ao[0]);
                }
            }
        }

        if (canSeeDown) {
            TextureAtlasSprite sp = model.stillMaterial().sprite();
            float minU = sp.getU0(), maxU = sp.getU1(), minV = sp.getV0(), maxV = sp.getV1();
            this.compileLighting(level, pos, Direction.DOWN, ao, li);
            
            pushV(builder, bx + 0.0f, by + EPSILON, bz + 1.0f, color, minU, maxV, 0, -1, 0, li[0], ao[0]);
            pushV(builder, bx + 0.0f, by + EPSILON, bz + 0.0f, color, minU, minV, 0, -1, 0, li[1], ao[1]);
            pushV(builder, bx + 1.0f, by + EPSILON, bz + 0.0f, color, maxU, minV, 0, -1, 0, li[2], ao[2]);
            pushV(builder, bx + 1.0f, by + EPSILON, bz + 1.0f, color, maxU, maxV, 0, -1, 0, li[3], ao[3]);
        }

        if (canSeeNorth && this.isExposedOffset(level, blockState, pos, Direction.NORTH, Math.max(hNW, hNE))) emitFace(level, pos, builder, bx, by, bz, hNW, hNE, EPSILON, 0, 1, color, model, Direction.NORTH, ao, li);
        if (canSeeSouth && this.isExposedOffset(level, blockState, pos, Direction.SOUTH, Math.max(hSE, hSW))) emitFace(level, pos, builder, bx, by, bz, hSE, hSW, 1.0f - EPSILON, 1, 0, color, model, Direction.SOUTH, ao, li);
        if (canSeeWest && this.isExposedOffset(level, blockState, pos, Direction.WEST, Math.max(hSW, hNW)))  emitFace(level, pos, builder, bx, by, bz, hSW, hNW, EPSILON, 0, 1, color, model, Direction.WEST, ao, li);
        if (canSeeEast && this.isExposedOffset(level, blockState, pos, Direction.EAST, Math.max(hNE, hSE)))  emitFace(level, pos, builder, bx, by, bz, hNE, hSE, 1.0f - EPSILON, 1, 0, color, model, Direction.EAST, ao, li);
    }

    private void emitFace(BlockAndTintGetter level, BlockPos pos, VertexConsumer builder, float bx, float by, float bz, float hl, float hr, float zOff, float x0, float x1, int color, FluidModel model, Direction dir, float[] ao, int[] lights) {
        TextureAtlasSprite sp = model.flowingMaterial().sprite();
        float minU = sp.getU(0.0f), maxU = sp.getU(0.5f);
        float v0 = sp.getV((1.0f - hl) * 0.5f);
        float v1 = sp.getV((1.0f - hr) * 0.5f);
        float minV = sp.getV(0.5f);

        float nx = dir.getStepX(), ny = dir.getStepY(), nz = dir.getStepZ();
        this.compileLighting(level, pos, dir, ao, lights);
        
        // Outward Quad
        switch (dir) {
            case NORTH -> {
                pushV(builder, bx + x1, by + hr, bz + zOff, color, maxU, v1, nx, ny, nz, lights[2], ao[2]);
                pushV(builder, bx + x1, by + 0,  bz + zOff, color, maxU, minV, nx, ny, nz, lights[1], ao[1]);
                pushV(builder, bx + x0, by + 0,  bz + zOff, color, minU, minV, nx, ny, nz, lights[0], ao[0]);
                pushV(builder, bx + x0, by + hl, bz + zOff, color, minU, v0, nx, ny, nz, lights[3], ao[3]);
            }
            case SOUTH -> {
                pushV(builder, bx + x1, by + hr, bz + zOff, color, minU, v1, nx, ny, nz, lights[2], ao[2]);
                pushV(builder, bx + x1, by + 0,  bz + zOff, color, minU, minV, nx, ny, nz, lights[1], ao[1]);
                pushV(builder, bx + x0, by + 0,  bz + zOff, color, maxU, minV, nx, ny, nz, lights[0], ao[0]);
                pushV(builder, bx + x0, by + hl, bz + zOff, color, maxU, v0, nx, ny, nz, lights[3], ao[3]);
            }
            case WEST -> {
                pushV(builder, bx + zOff, by + hr, bz + x0, color, maxU, v1, nx, ny, nz, lights[2], ao[2]);
                pushV(builder, bx + zOff, by + 0,  bz + x0, color, maxU, minV, nx, ny, nz, lights[1], ao[1]);
                pushV(builder, bx + zOff, by + 0,  bz + x1, color, minU, minV, nx, ny, nz, lights[0], ao[0]);
                pushV(builder, bx + zOff, by + hl, bz + x1, color, minU, v0, nx, ny, nz, lights[3], ao[3]);
            }
            case EAST -> {
                pushV(builder, bx + zOff, by + hr, bz + x0, color, minU, v1, nx, ny, nz, lights[2], ao[2]);
                pushV(builder, bx + zOff, by + 0,  bz + x0, color, minU, minV, nx, ny, nz, lights[1], ao[1]);
                pushV(builder, bx + zOff, by + 0,  bz + x1, color, maxU, minV, nx, ny, nz, lights[0], ao[0]);
                pushV(builder, bx + zOff, by + hl, bz + x1, color, maxU, v0, nx, ny, nz, lights[3], ao[3]);
            }
        }
        
        // Inward quad rendering when needed (water wall visible from inside)
        switch (dir) {
            case NORTH -> {
                pushV(builder, bx + x0, by + hl, bz + zOff, color, minU, v0, -nx, -ny, -nz, lights[3], ao[3]);
                pushV(builder, bx + x0, by + 0,  bz + zOff, color, minU, minV, -nx, -ny, -nz, lights[0], ao[0]);
                pushV(builder, bx + x1, by + 0,  bz + zOff, color, maxU, minV, -nx, -ny, -nz, lights[1], ao[1]);
                pushV(builder, bx + x1, by + hr, bz + zOff, color, maxU, v1, -nx, -ny, -nz, lights[2], ao[2]);
            }
            case SOUTH -> {
                pushV(builder, bx + x0, by + hl, bz + zOff, color, maxU, v0, -nx, -ny, -nz, lights[3], ao[3]);
                pushV(builder, bx + x0, by + 0,  bz + zOff, color, maxU, minV, -nx, -ny, -nz, lights[0], ao[0]);
                pushV(builder, bx + x1, by + 0,  bz + zOff, color, minU, minV, -nx, -ny, -nz, lights[1], ao[1]);
                pushV(builder, bx + x1, by + hr, bz + zOff, color, minU, v1, -nx, -ny, -nz, lights[2], ao[2]);
            }
            case WEST -> {
                pushV(builder, bx + zOff, by + hl, bz + x1, color, minU, v0, -nx, -ny, -nz, lights[3], ao[3]);
                pushV(builder, bx + zOff, by + 0,  bz + x1, color, minU, minV, -nx, -ny, -nz, lights[0], ao[0]);
                pushV(builder, bx + zOff, by + 0,  bz + x0, color, maxU, minV, -nx, -ny, -nz, lights[1], ao[1]);
                pushV(builder, bx + zOff, by + hr, bz + x0, color, maxU, v1, -nx, -ny, -nz, lights[2], ao[2]);
            }
            case EAST -> {
                pushV(builder, bx + zOff, by + hl, bz + x1, color, maxU, v0, -nx, -ny, -nz, lights[3], ao[3]);
                pushV(builder, bx + zOff, by + 0,  bz + x1, color, maxU, minV, -nx, -ny, -nz, lights[0], ao[0]);
                pushV(builder, bx + zOff, by + 0,  bz + x0, color, minU, minV, -nx, -ny, -nz, lights[1], ao[1]);
                pushV(builder, bx + zOff, by + hr, bz + x0, color, minU, v1, -nx, -ny, -nz, lights[2], ao[2]);
            }
        }
    }

    private void pushV(VertexConsumer b, float x, float y, float z, int color, float u, float v, float nx, float ny, float nz, int light, float shade) {
        b.addVertex(x, y, z)
         .setColor((int) (ARGB.red(color) * shade), (int) (ARGB.green(color) * shade), (int) (ARGB.blue(color) * shade), ARGB.alpha(color))
         .setUv(u, v)
         .setLight(light)
         .setNormal(nx, ny, nz);
    }
    
    private static class GeometryOcclusionCache {
        private static final int LIMIT = 512;
        private final Object2IntLinkedOpenCustomHashMap<Entry> table = new Object2IntLinkedOpenCustomHashMap<>(LIMIT, 0.5f, Entry.Strategy.INSTANCE);
        private final Entry lookupObj = new Entry();

        public GeometryOcclusionCache() {
            this.table.defaultReturnValue(-1);
        }
        public static boolean isFull(VoxelShape shape) { return shape == Shapes.block(); }
        public static boolean isEmpty(VoxelShape shape) { return shape == Shapes.empty() || shape.isEmpty(); }

        public boolean check(VoxelShape a, VoxelShape b) { return this.check(a, b, null); }
        public boolean check(VoxelShape a, VoxelShape b, VoxelShape limit) {
            this.lookupObj.a = a; this.lookupObj.b = b; this.lookupObj.limit = limit;
            int v = this.table.getAndMoveToFirst(this.lookupObj);
            if (v != -1) return v == 1;
            
            VoxelShape merged = a;
            if (limit != null && !limit.isEmpty()) merged = Shapes.join(a, limit, BooleanOp.ONLY_FIRST);
            boolean r = Shapes.joinIsNotEmpty(merged, b, BooleanOp.ONLY_FIRST);
            
            if (this.table.size() >= LIMIT) this.table.removeLastInt();
            this.table.putAndMoveToFirst(this.lookupObj.copy(), r ? 1 : 0);
            return r;
        }

        private static class Entry {
            VoxelShape a, b, limit;
            Entry copy() { Entry c = new Entry(); c.a = a; c.b = b; c.limit = limit; return c; }
            static class Strategy implements Hash.Strategy<Entry> {
                static final Strategy INSTANCE = new Strategy();
                public int hashCode(Entry o) { return System.identityHashCode(o.a) * 31 * 31 + System.identityHashCode(o.b) * 31 + System.identityHashCode(o.limit); }
                public boolean equals(Entry a, Entry b) { return a == b || (a != null && b != null && a.a == b.a && a.b == b.b && a.limit == b.limit); }
            }
        }
    }
}
