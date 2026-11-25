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

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class XenoColorRegistry {
    private static final Map<Block, ColorResolver> RESOLVERS = new Reference2ObjectOpenHashMap<>();

    static {
        register(BiomeColors.GRASS_COLOR_RESOLVER,
            Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.TALL_GRASS,
            Blocks.FERN, Blocks.LARGE_FERN, Blocks.POTTED_FERN,
            Blocks.BUSH, Blocks.PINK_PETALS, Blocks.WILDFLOWERS,
            Blocks.SUGAR_CANE
        );
        register(BiomeColors.FOLIAGE_COLOR_RESOLVER,
            Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.VINE
        );
        register(BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER,
            Blocks.LEAF_LITTER
        );
        register(BiomeColors.WATER_COLOR_RESOLVER,
            Blocks.WATER
        );
    }

    private XenoColorRegistry() {
    }

    private static void register(ColorResolver resolver, Block... blocks) {
        for (int i = 0; i < blocks.length; i++) {
            RESOLVERS.put(blocks[i], resolver);
        }
    }

    public static @Nullable ColorResolver getResolver(BlockState state) {
        return RESOLVERS.get(state.getBlock());
    }

    public static int sampleVertexColor(
        BlockAndTintGetter level,
        BlockPos pos,
        BlockPos.MutableBlockPos scratchPos,
        ColorResolver resolver,
        float lx,
        float lz
    ) {
        int bx = pos.getX();
        int by = pos.getY();
        int bz = pos.getZ();

        int x0 = bx + (lx < 0.5F ? -1 : 0);
        int z0 = bz + (lz < 0.5F ? -1 : 0);
        float fx = lx < 0.5F ? lx + 0.5F : lx - 0.5F;
        float fz = lz < 0.5F ? lz + 0.5F : lz - 0.5F;

        int c00 = level.getBlockTint(scratchPos.set(x0, by, z0), resolver);
        int c10 = level.getBlockTint(scratchPos.set(x0 + 1, by, z0), resolver);
        int c01 = level.getBlockTint(scratchPos.set(x0, by, z0 + 1), resolver);
        int c11 = level.getBlockTint(scratchPos.set(x0 + 1, by, z0 + 1), resolver);

        return bilerpRgb(c00, c10, c01, c11, fx, fz);
    }

    private static int bilerpRgb(int c00, int c10, int c01, int c11, float fx, float fz) {
        float w00 = (1.0F - fx) * (1.0F - fz);
        float w10 = fx * (1.0F - fz);
        float w01 = (1.0F - fx) * fz;
        float w11 = fx * fz;

        int r = Math.round(((c00 >>> 16) & 0xFF) * w00 + ((c10 >>> 16) & 0xFF) * w10 + ((c01 >>> 16) & 0xFF) * w01 + ((c11 >>> 16) & 0xFF) * w11);
        int g = Math.round(((c00 >>> 8) & 0xFF) * w00 + ((c10 >>> 8) & 0xFF) * w10 + ((c01 >>> 8) & 0xFF) * w01 + ((c11 >>> 8) & 0xFF) * w11);
        int b = Math.round((c00 & 0xFF) * w00 + (c10 & 0xFF) * w10 + (c01 & 0xFF) * w01 + (c11 & 0xFF) * w11);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
