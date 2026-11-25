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

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class XenoSectionTintCache {
    private static final int SECTION_SIZE = 16;
    private static final int SECTION_VOLUME = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    private static final int MAX_BLEND_RADIUS = 7;
    private static final int MAX_SLICE_SIZE = SECTION_SIZE + 2 * MAX_BLEND_RADIUS;
    private static final int MAX_SLICE_AREA = MAX_SLICE_SIZE * MAX_SLICE_SIZE;
    private static final int MAX_SLOTS = 8;

    private final ResolverSlot[] slots = new ResolverSlot[MAX_SLOTS];
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

    private int minX;
    private int minY;
    private int minZ;
    private int blendRadius;

    public void reset(SectionPos sectionPos, int blendRadius) {
        this.minX = sectionPos.minBlockX();
        this.minY = sectionPos.minBlockY();
        this.minZ = sectionPos.minBlockZ();
        this.blendRadius = Math.clamp(blendRadius, 0, MAX_BLEND_RADIUS);

        for (int i = 0; i < MAX_SLOTS; i++) {
            ResolverSlot slot = this.slots[i];
            if (slot != null) {
                slot.nextSection();
            }
        }
    }

    public int getBlockTint(ClientLevel level, BlockPos pos, ColorResolver resolver) {
        ResolverSlot slot = getOrCreateSlot(resolver);
        if (slot == null) {
            return level.getBlockTint(pos, resolver);
        }

        int bx = pos.getX();
        int by = pos.getY();
        int bz = pos.getZ();

        int relX = bx - this.minX;
        int relY = by - this.minY;
        int relZ = bz - this.minZ;

        if ((relX | relY | relZ) >= 0 && relX < SECTION_SIZE && relY < SECTION_SIZE && relZ < SECTION_SIZE) {
            int idx = (relY << 8) | (relZ << 4) | relX;
            if (slot.sectionEpoch[idx] == slot.currentSectionEpoch) {
                return slot.sectionColors[idx];
            }
            int color = computeBlendedColor(level, slot, bx, by, bz);
            slot.sectionColors[idx] = color;
            slot.sectionEpoch[idx] = slot.currentSectionEpoch;
            return color;
        }

        return computeBlendedColor(level, slot, bx, by, bz);
    }

    private int computeBlendedColor(ClientLevel level, ResolverSlot slot, int bx, int by, int bz) {
        int r = this.blendRadius;
        if (r <= 0) {
            return getRawColor(level, slot, bx, by, bz);
        }

        int totalR = 0;
        int totalG = 0;
        int totalB = 0;
        int count = 0;

        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int c = getRawColor(level, slot, bx + dx, by, bz + dz);
                totalR += (c >>> 16) & 0xFF;
                totalG += (c >>> 8) & 0xFF;
                totalB += c & 0xFF;
                count++;
            }
        }

        return 0xFF000000 | ((totalR / count) << 16) | ((totalG / count) << 8) | (totalB / count);
    }

    private int getRawColor(ClientLevel level, ResolverSlot slot, int x, int y, int z) {
        if (slot.rawY != y) {
            slot.rawY = y;
            slot.nextRawSlice();
        }

        int sx = x - this.minX + MAX_BLEND_RADIUS;
        int sz = z - this.minZ + MAX_BLEND_RADIUS;
        if ((sx | sz) >= 0 && sx < MAX_SLICE_SIZE && sz < MAX_SLICE_SIZE) {
            int idx = sz * MAX_SLICE_SIZE + sx;
            if (slot.rawEpoch[idx] == slot.currentRawEpoch) {
                return slot.rawColors[idx];
            }
            Biome biome = level.getBiome(this.scratchPos.set(x, y, z)).value();
            int color = 0xFF000000 | slot.resolver.getColor(biome, x, z);
            slot.rawColors[idx] = color;
            slot.rawEpoch[idx] = slot.currentRawEpoch;
            return color;
        }

        Biome biome = level.getBiome(this.scratchPos.set(x, y, z)).value();
        return 0xFF000000 | slot.resolver.getColor(biome, x, z);
    }

    private @Nullable ResolverSlot getOrCreateSlot(ColorResolver resolver) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            ResolverSlot slot = this.slots[i];
            if (slot == null) {
                slot = new ResolverSlot(resolver);
                this.slots[i] = slot;
                return slot;
            }
            if (slot.resolver == resolver) {
                return slot;
            }
        }
        return null;
    }

    private static final class ResolverSlot {
        final ColorResolver resolver;
        final int[] sectionColors = new int[SECTION_VOLUME];
        final int[] sectionEpoch = new int[SECTION_VOLUME];
        final int[] rawColors = new int[MAX_SLICE_AREA];
        final int[] rawEpoch = new int[MAX_SLICE_AREA];
        int currentSectionEpoch = 1;
        int currentRawEpoch = 1;
        int rawY = Integer.MIN_VALUE;

        ResolverSlot(ColorResolver resolver) {
            this.resolver = resolver;
        }

        void nextSection() {
            this.currentSectionEpoch++;
            if (this.currentSectionEpoch == 0) {
                Arrays.fill(this.sectionEpoch, 0);
                this.currentSectionEpoch = 1;
            }
            this.rawY = Integer.MIN_VALUE;
        }

        void nextRawSlice() {
            this.currentRawEpoch++;
            if (this.currentRawEpoch == 0) {
                Arrays.fill(this.rawEpoch, 0);
                this.currentRawEpoch = 1;
            }
        }
    }
}
