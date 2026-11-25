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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class XenoLightDataCache {
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private static final int SECTION_BLOCK_LENGTH = 16 + 2 * NEIGHBOR_BLOCK_RADIUS;
    private static final int SINGLE_BLOCK_LENGTH = 1 + 2 * NEIGHBOR_BLOCK_RADIUS;
    private static final ThreadLocal<XenoLightDataCache> THREAD_LOCAL = ThreadLocal.withInitial(XenoLightDataCache::new);

    private static final int BLOCK_LIGHT_MASK = 0xF;
    private static final int SKY_LIGHT_SHIFT = 4;
    private static final int SKY_LIGHT_MASK = 0xF;
    private static final int SHADE_SHIFT = 8;
    private static final int SHADE_MASK = 0xFF;
    private static final float SHADE_UNPACK = 1.0F / 255.0F;
    private static final int POPULATED_BIT = 1 << 31;

    private final int blockLength;
    private final BlockState[] states;
    private final int[] lightData;
    private int baseX;
    private int baseY;
    private int baseZ;

    public XenoLightDataCache() {
        this(SECTION_BLOCK_LENGTH);
    }

    private XenoLightDataCache(int blockLength) {
        this.blockLength = blockLength;
        int count = blockLength * blockLength * blockLength;
        this.states = new BlockState[count];
        this.lightData = new int[count];
    }

    public static XenoLightDataCache forSingleBlock() {
        return new XenoLightDataCache(SINGLE_BLOCK_LENGTH);
    }

    public static XenoLightDataCache get() {
        return THREAD_LOCAL.get();
    }

    public void reset(BlockPos pos) {
        this.baseX = pos.getX();
        this.baseY = pos.getY();
        this.baseZ = pos.getZ();
        Arrays.fill(this.states, null);
        Arrays.fill(this.lightData, 0);
    }

    public void reset(SectionPos sectionPos) {
        this.baseX = sectionPos.minBlockX();
        this.baseY = sectionPos.minBlockY();
        this.baseZ = sectionPos.minBlockZ();
        Arrays.fill(this.states, null);
        Arrays.fill(this.lightData, 0);
    }

    public void bindSection(int sectionX, int sectionY, int sectionZ) {
        int minX = sectionX << 4;
        int minY = sectionY << 4;
        int minZ = sectionZ << 4;
        if (this.baseX == minX && this.baseY == minY && this.baseZ == minZ) {
            return;
        }
        this.baseX = minX;
        this.baseY = minY;
        this.baseZ = minZ;
        Arrays.fill(this.states, null);
        Arrays.fill(this.lightData, 0);
    }

    public BlockState getState(BlockAndTintGetter level, BlockPos pos) {
        int relX = pos.getX() - this.baseX + NEIGHBOR_BLOCK_RADIUS;
        int relY = pos.getY() - this.baseY + NEIGHBOR_BLOCK_RADIUS;
        int relZ = pos.getZ() - this.baseZ + NEIGHBOR_BLOCK_RADIUS;
        int len = this.blockLength;
        if (isOutOfBounds(relX, relY, relZ, len)) {
            return level.getBlockState(pos);
        }
        int index = (relX * len + relY) * len + relZ;
        BlockState state = this.states[index];
        if (state == null) {
            state = level.getBlockState(pos);
            this.states[index] = state;
        }
        return state;
    }

    public int getLightCoords(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (state.emissiveRendering()) {
            return LightCoordsUtil.FULL_BRIGHT;
        }
        int packed = getRawLightCoords(level, pos);
        int blockLight = LightCoordsUtil.block(packed);
        int emission = state.getLightEmission();
        if (blockLight < emission) {
            return LightCoordsUtil.withBlock(packed, emission);
        }
        return packed;
    }

    public int getRawLightCoords(BlockAndTintGetter level, BlockPos pos) {
        int relX = pos.getX() - this.baseX + NEIGHBOR_BLOCK_RADIUS;
        int relY = pos.getY() - this.baseY + NEIGHBOR_BLOCK_RADIUS;
        int relZ = pos.getZ() - this.baseZ + NEIGHBOR_BLOCK_RADIUS;
        int len = this.blockLength;
        if (isOutOfBounds(relX, relY, relZ, len)) {
            return BrightnessGetter.DEFAULT.packedBrightness(level, pos);
        }
        int index = (relX * len + relY) * len + relZ;
        int word = this.lightData[index];
        if ((word & POPULATED_BIT) == 0) {
            word = populateLight(level, pos, index);
        }
        int block = word & BLOCK_LIGHT_MASK;
        int sky = (word >>> SKY_LIGHT_SHIFT) & SKY_LIGHT_MASK;
        return LightCoordsUtil.pack(block, sky);
    }

    public float getShadeBrightness(BlockAndTintGetter level, BlockPos pos) {
        int relX = pos.getX() - this.baseX + NEIGHBOR_BLOCK_RADIUS;
        int relY = pos.getY() - this.baseY + NEIGHBOR_BLOCK_RADIUS;
        int relZ = pos.getZ() - this.baseZ + NEIGHBOR_BLOCK_RADIUS;
        int len = this.blockLength;
        if (isOutOfBounds(relX, relY, relZ, len)) {
            return level.getBlockState(pos).getShadeBrightness(level, pos);
        }
        int index = (relX * len + relY) * len + relZ;
        int word = this.lightData[index];
        if ((word & POPULATED_BIT) == 0) {
            word = populateLight(level, pos, index);
        }
        return ((word >>> SHADE_SHIFT) & SHADE_MASK) * SHADE_UNPACK;
    }

    private int populateLight(BlockAndTintGetter level, BlockPos pos, int index) {
        BlockState state = this.states[index];
        if (state == null) {
            state = level.getBlockState(pos);
            this.states[index] = state;
        }
        int packed = BrightnessGetter.DEFAULT.packedBrightness(level, pos);
        int block = LightCoordsUtil.block(packed) & BLOCK_LIGHT_MASK;
        int sky = LightCoordsUtil.sky(packed) & SKY_LIGHT_MASK;
        int shade = Math.round(Mth.clamp(state.getShadeBrightness(level, pos), 0.0F, 1.0F) * 255.0F) & SHADE_MASK;
        int word = POPULATED_BIT | (shade << SHADE_SHIFT) | (sky << SKY_LIGHT_SHIFT) | block;
        this.lightData[index] = word;
        return word;
    }

    private static boolean isOutOfBounds(int relX, int relY, int relZ, int len) {
        return (relX | relY | relZ) < 0 || relX >= len || relY >= len || relZ >= len;
    }
}
