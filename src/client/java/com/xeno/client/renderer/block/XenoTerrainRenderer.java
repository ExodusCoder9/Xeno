package com.xeno.client.renderer.block;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.client.renderer.biome.XenoBiomeBlender;
import com.xeno.client.renderer.light.XenoFlatLightPipeline;
import com.xeno.client.renderer.world.XenoLevelSlice;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Monolithic, highly optimized custom Terrain Renderer.
 * Designed to completely replace Vanilla's ModelBlockRenderer.
 * Bypasses BlockPos allocations and slow Vanilla light interpolation.
 */

public class XenoTerrainRenderer {

    private static final Direction[] DIRECTIONS = Direction.values();
    private final boolean ambientOcclusion;
    private final List<BlockStateModelPart> parts = new ArrayList<>();
    private final QuadInstance quadInstance = new QuadInstance();
    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

    public XenoTerrainRenderer(boolean ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
    }

    public void tesselateBlock(
            XenoLevelSlice slice,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            BlockStateModel model,
            VertexConsumer builder,
            RandomSource random,
            long seed
    ) {
        random.setSeed(seed);
        this.parts.clear();
        model.collectParts(random, this.parts);
        if (this.parts.isEmpty()) return;

        boolean useAo = this.parts.getFirst().useAmbientOcclusion();
        boolean ao = this.ambientOcclusion && state.getLightEmission() == 0 && useAo;
        
        Vec3 offset = state.getOffset(pos);
        float x = pos.getX() + (float) offset.x;
        float y = pos.getY() + (float) offset.y;
        float z = pos.getZ() + (float) offset.z;
        
        int px = pos.getX();
        int py = pos.getY();
        int pz = pos.getZ();

        for (BlockStateModelPart part : this.parts) {
            // 1. Directional (Culled) Quads
            for (Direction dir : DIRECTIONS) {
                List<BakedQuad> quads = part.getQuads(dir);
                if (!quads.isEmpty()) {
                    BlockState neighborState = slice.getBlockState(px + dir.getStepX(), py + dir.getStepY(), pz + dir.getStepZ());
                    if (Block.shouldRenderFace(state, neighborState, dir)) {
                        renderQuads(slice, level, state, px, py, pz, x, y, z, builder, quads, ao, true);
                    }
                }
            }
            
            // 2. Unculled Quads (e.g. cross models like grass, interior elements)
            List<BakedQuad> unculledQuads = part.getQuads(null);
            if (!unculledQuads.isEmpty()) {
                renderQuads(slice, level, state, px, py, pz, x, y, z, builder, unculledQuads, ao, false);
            }
        }
        this.parts.clear();
    }

    private static final float[] AO_TABLE = new float[] { 1.0F, 0.8F, 0.6F, 0.4F };

    private void renderQuads(
            XenoLevelSlice slice,
            BlockAndTintGetter level,
            BlockState state,
            int px, int py, int pz,
            float x, float y, float z,
            VertexConsumer builder,
            List<BakedQuad> quads,
            boolean ao,
            boolean isCulled
    ) {
        float[] aoWeights = new float[4];
        int[] lights = new int[4];
        
        for (BakedQuad quad : quads) {
            Direction quadDir = quad.direction();
            
            // Calculate biome/block tinting
            int tintColor = 0xFFFFFFFF;
            if (quad.materialInfo().tintIndex() != -1) {
                tintColor = XenoBiomeBlender.blendColor(slice, px, pz, 1, (bx, bz) -> 0xFFFFFFFF);
            }

            boolean quadAo = ao && quad.materialInfo().shade();

            if (quadAo) {
                calculateQuadSmoothLighting(slice, level, px, py, pz, quad, aoWeights, lights, isCulled);
            } else {
                int lx = isCulled ? px + quadDir.getStepX() : px;
                int ly = isCulled ? py + quadDir.getStepY() : py;
                int lz = isCulled ? pz + quadDir.getStepZ() : pz;
                
                this.lightPos.set(lx, ly, lz);
                int light = LightCoordsUtil.getLightCoords(level, this.lightPos);
                float flatAo = quad.materialInfo().shade() ? XenoFlatLightPipeline.getFaceShade(quadDir) : 1.0F;
                
                aoWeights[0] = flatAo; aoWeights[1] = flatAo; aoWeights[2] = flatAo; aoWeights[3] = flatAo;
                lights[0] = light; lights[1] = light; lights[2] = light; lights[3] = light;
            }

            // Setup QuadInstance and emit
            for (int i = 0; i < 4; i++) {
                int vertexColor = multiplyColor(tintColor, aoWeights[i]);
                this.quadInstance.setColor(i, vertexColor);
                this.quadInstance.setLightCoords(i, lights[i]);
            }

            builder.putBlockBakedQuad(x, y, z, quad, this.quadInstance);
        }
    }
    
    private void calculateQuadSmoothLighting(XenoLevelSlice slice, BlockAndTintGetter level, int cx, int cy, int cz, BakedQuad quad, float[] outAo, int[] outLight, boolean isCulled) {
        Direction face = quad.direction();
        int fx = isCulled ? cx + face.getStepX() : cx;
        int fy = isCulled ? cy + face.getStepY() : cy;
        int fz = isCulled ? cz + face.getStepZ() : cz;
        float shade = quad.materialInfo().shade() ? XenoFlatLightPipeline.getFaceShade(face) : 1.0F;
        
        int cl = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx, fy, fz));
        int ux = 0, uy = 0, uz = 0;
        int vx = 0, vy = 0, vz = 0;
        
        switch (face.getAxis()) {
            case Y -> { ux = 1; vz = 1; }
            case Z -> { ux = 1; vy = 1; }
            case X -> { uz = 1; vy = 1; }
        }

        boolean o_nU = slice.getBlockState(fx - ux, fy - uy, fz - uz).isSolidRender();
        boolean o_pU = slice.getBlockState(fx + ux, fy + uy, fz + uz).isSolidRender();
        boolean o_nV = slice.getBlockState(fx - vx, fy - vy, fz - vz).isSolidRender();
        boolean o_pV = slice.getBlockState(fx + vx, fy + vy, fz + vz).isSolidRender();
        
        boolean o_nUnV = slice.getBlockState(fx - ux - vx, fy - uy - vy, fz - uz - vz).isSolidRender();
        boolean o_pUnV = slice.getBlockState(fx + ux - vx, fy + uy - vy, fz + uz - vz).isSolidRender();
        boolean o_nUpV = slice.getBlockState(fx - ux + vx, fy - uy + vy, fz - uz + vz).isSolidRender();
        boolean o_pUpV = slice.getBlockState(fx + ux + vx, fy + uy + vy, fz + uz + vz).isSolidRender();

        int l_nU = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - ux, fy - uy, fz - uz));
        int l_pU = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + ux, fy + uy, fz + uz));
        int l_nV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - vx, fy - vy, fz - vz));
        int l_pV = LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + vx, fy + vy, fz + vz));
        
        int l_nUnV = (o_nU && o_nV) ? l_nU : LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - ux - vx, fy - uy - vy, fz - uz - vz));
        int l_pUnV = (o_pU && o_nV) ? l_pU : LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + ux - vx, fy + uy - vy, fz + uz - vz));
        int l_nUpV = (o_nU && o_pV) ? l_nU : LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx - ux + vx, fy - uy + vy, fz - uz + vz));
        int l_pUpV = (o_pU && o_pV) ? l_pU : LightCoordsUtil.getLightCoords(level, this.lightPos.set(fx + ux + vx, fy + uy + vy, fz + uz + vz));

        int occ_nUnV = (o_nU ? 1 : 0) + (o_nV ? 1 : 0) + ((o_nU && o_nV) ? 1 : (o_nUnV ? 1 : 0));
        int cb = cl & 0xFFFF, cs = (cl >> 16) & 0xFFFF;
        int c_nUnV = buildLight((cb + (l_nU & 0xFFFF) + (l_nV & 0xFFFF) + (l_nUnV & 0xFFFF)) / 4, (cs + ((l_nU >> 16) & 0xFFFF) + ((l_nV >> 16) & 0xFFFF) + ((l_nUnV >> 16) & 0xFFFF)) / 4);

        int occ_pUnV = (o_pU ? 1 : 0) + (o_nV ? 1 : 0) + ((o_pU && o_nV) ? 1 : (o_pUnV ? 1 : 0));
        int c_pUnV = buildLight((cb + (l_pU & 0xFFFF) + (l_nV & 0xFFFF) + (l_pUnV & 0xFFFF)) / 4, (cs + ((l_pU >> 16) & 0xFFFF) + ((l_nV >> 16) & 0xFFFF) + ((l_pUnV >> 16) & 0xFFFF)) / 4);

        int occ_nUpV = (o_nU ? 1 : 0) + (o_pV ? 1 : 0) + ((o_nU && o_pV) ? 1 : (o_nUpV ? 1 : 0));
        int c_nUpV = buildLight((cb + (l_nU & 0xFFFF) + (l_pV & 0xFFFF) + (l_nUpV & 0xFFFF)) / 4, (cs + ((l_nU >> 16) & 0xFFFF) + ((l_pV >> 16) & 0xFFFF) + ((l_nUpV >> 16) & 0xFFFF)) / 4);

        int occ_pUpV = (o_pU ? 1 : 0) + (o_pV ? 1 : 0) + ((o_pU && o_pV) ? 1 : (o_pUpV ? 1 : 0));
        int c_pUpV = buildLight((cb + (l_pU & 0xFFFF) + (l_pV & 0xFFFF) + (l_pUpV & 0xFFFF)) / 4, (cs + ((l_pU >> 16) & 0xFFFF) + ((l_pV >> 16) & 0xFFFF) + ((l_pUpV >> 16) & 0xFFFF)) / 4);

        for (int i = 0; i < 4; i++) {
            org.joml.Vector3fc pos = quad.position(i);
            boolean pU = face.getAxis() == Direction.Axis.X ? pos.z() > 0.5f : pos.x() > 0.5f;
            boolean pV = face.getAxis() == Direction.Axis.Y ? pos.z() > 0.5f : pos.y() > 0.5f;

            if (!pU && !pV) {
                outAo[i] = AO_TABLE[occ_nUnV] * shade; outLight[i] = c_nUnV;
            } else if (pU && !pV) {
                outAo[i] = AO_TABLE[occ_pUnV] * shade; outLight[i] = c_pUnV;
            } else if (!pU && pV) {
                outAo[i] = AO_TABLE[occ_nUpV] * shade; outLight[i] = c_nUpV;
            } else {
                outAo[i] = AO_TABLE[occ_pUpV] * shade; outLight[i] = c_pUpV;
            }
        }
    }
    
    private static int buildLight(int block, int sky) {
        return block | (sky << 16);
    }
    
    private static int multiplyColor(int color, float multiplier) {
        int r = (int) (((color >> 16) & 0xFF) * multiplier);
        int g = (int) (((color >> 8) & 0xFF) * multiplier);
        int b = (int) ((color & 0xFF) * multiplier);
        int a = (color >> 24) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
