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

import java.util.Arrays;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.LightCoordsUtil.BrightnessGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class XenoLightDataCache {
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private static final int BLOCK_LENGTH = 16 + 2 * NEIGHBOR_BLOCK_RADIUS;
    private static final int BLOCK_COUNT = BLOCK_LENGTH * BLOCK_LENGTH * BLOCK_LENGTH;
    private static final ThreadLocal<XenoLightDataCache> THREAD_LOCAL = ThreadLocal.withInitial(XenoLightDataCache::new);

    private final BlockState[] states = new BlockState[BLOCK_COUNT];
    private final int[] packedBrightness = new int[BLOCK_COUNT];
    private final float[] shade = new float[BLOCK_COUNT];
    private final int[] epoch = new int[BLOCK_COUNT];
    private int currentEpoch = 1;
    private int baseX;
    private int baseY;
    private int baseZ;

    public static XenoLightDataCache get() {
        return THREAD_LOCAL.get();
    }

    public void reset(SectionPos sectionPos) {
        this.baseX = sectionPos.minBlockX();
        this.baseY = sectionPos.minBlockY();
        this.baseZ = sectionPos.minBlockZ();
        this.currentEpoch++;
        if (this.currentEpoch == 0) {
            Arrays.fill(this.epoch, 0);
            this.currentEpoch = 1;
        }
    }

    public BlockState getState(BlockAndTintGetter level, BlockPos pos) {
        int index = this.index(pos);
        if (this.epoch[index] != this.currentEpoch) {
            this.populate(level, pos, index);
        }
        return this.states[index];
    }

    public int getLightCoords(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (state.emissiveRendering()) {
            return LightCoordsUtil.FULL_BRIGHT;
        }
        int index = this.index(pos);
        if (this.epoch[index] != this.currentEpoch) {
            this.populate(level, pos, index);
        }
        int packed = this.packedBrightness[index];
        int blockLight = LightCoordsUtil.block(packed);
        int emission = state.getLightEmission();
        if (blockLight < emission) {
            return LightCoordsUtil.withBlock(packed, emission);
        }
        return packed;
    }

    public float getShadeBrightness(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int index = this.index(pos);
        if (this.epoch[index] != this.currentEpoch) {
            this.populate(level, pos, index);
        }
        return this.shade[index];
    }

    private void populate(BlockAndTintGetter level, BlockPos pos, int index) {
        BlockState state = level.getBlockState(pos);
        this.states[index] = state;
        this.packedBrightness[index] = BrightnessGetter.DEFAULT.packedBrightness(level, pos);
        this.shade[index] = state.getShadeBrightness(level, pos);
        this.epoch[index] = this.currentEpoch;
    }

    private int index(BlockPos pos) {
        return ((pos.getX() - this.baseX + NEIGHBOR_BLOCK_RADIUS) * BLOCK_LENGTH + (pos.getY() - this.baseY + NEIGHBOR_BLOCK_RADIUS))
            * BLOCK_LENGTH
            + (pos.getZ() - this.baseZ + NEIGHBOR_BLOCK_RADIUS);
    }
}
