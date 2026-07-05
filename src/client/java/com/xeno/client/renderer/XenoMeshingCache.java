package com.xeno.client.renderer;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.util.LightCoordsUtil;

public class XenoMeshingCache {
    private static final ThreadLocal<XenoMeshingCache> INSTANCE = ThreadLocal.withInitial(XenoMeshingCache::new);

    public static XenoMeshingCache get() {
        return INSTANCE.get();
    }

    public boolean active = false;
    private int minX, minY, minZ;
    private final BlockState[] blockStates = new BlockState[18 * 18 * 18];
    private final int[] lightCoords = new int[18 * 18 * 18];
    private final float[] shadeBrightness = new float[18 * 18 * 18];

    public void init(BlockAndTintGetter region, BlockPos origin) {
        this.minX = origin.getX() - 1;
        this.minY = origin.getY() - 1;
        this.minZ = origin.getZ() - 1;
        this.active = true;

        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 18; y++) {
            for (int z = 0; z < 18; z++) {
                for (int x = 0; x < 18; x++) {
                    int idx = x + y * 18 + z * 18 * 18;
                    mut.set(this.minX + x, this.minY + y, this.minZ + z);
                    this.blockStates[idx] = region.getBlockState(mut);
                    this.lightCoords[idx] = -1;
                    this.shadeBrightness[idx] = -1.0f;
                }
            }
        }
    }

    public void disable() {
        this.active = false;
        // Help GC by clearing references
        java.util.Arrays.fill(this.blockStates, null);
    }

    private int getIndex(BlockPos pos) {
        int dx = pos.getX() - this.minX;
        int dy = pos.getY() - this.minY;
        int dz = pos.getZ() - this.minZ;
        if (dx < 0 || dx >= 18 || dy < 0 || dy >= 18 || dz < 0 || dz >= 18) {
            return -1;
        }
        return dx + dy * 18 + dz * 18 * 18;
    }

    public BlockState getBlockState(BlockAndTintGetter region, BlockPos pos) {
        if (!this.active) return region.getBlockState(pos);
        int idx = getIndex(pos);
        if (idx == -1) return region.getBlockState(pos);
        return this.blockStates[idx];
    }

    public int getLightCoords(BlockAndTintGetter region, BlockState state, BlockPos pos) {
        if (!this.active) {
            return LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, region, state, pos);
        }
        int idx = getIndex(pos);
        if (idx == -1) {
            return LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, region, state, pos);
        }

        int val = this.lightCoords[idx];
        if (val == -1) {
            val = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, region, state, pos);
            this.lightCoords[idx] = val;
        }
        return val;
    }

    public float getShadeBrightness(BlockAndTintGetter region, BlockState state, BlockPos pos) {
        if (!this.active) {
            return state.getShadeBrightness(region, pos);
        }
        int idx = getIndex(pos);
        if (idx == -1) {
            return state.getShadeBrightness(region, pos);
        }

        float val = this.shadeBrightness[idx];
        if (val == -1.0f) {
            val = state.getShadeBrightness(region, pos);
            this.shadeBrightness[idx] = val;
        }
        return val;
    }
}
