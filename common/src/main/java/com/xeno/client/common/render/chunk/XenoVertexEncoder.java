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

package com.xeno.client.common.render.chunk;

import net.minecraft.util.Mth;

public final class XenoVertexEncoder {
    public static final float MODEL_ORIGIN = 8.0F;
    public static final float MODEL_RANGE = 32.0F;
    public static final int POSITION_BITS = 20;
    public static final int POSITION_MAX_COORD = 1 << POSITION_BITS; // 1048576
    public static final float POSITION_QUANT_SCALE = POSITION_MAX_COORD / MODEL_RANGE; // 32768.0F
    public static final float POSITION_DEQUANT_SCALE = MODEL_RANGE / (float) POSITION_MAX_COORD;

    private XenoVertexEncoder() {
    }

    public static int quantizePosition(float coord) {
        float normalized = coord + MODEL_ORIGIN;
        int quantized = (int) Math.floor(normalized * POSITION_QUANT_SCALE + 0.5F);
        return Mth.clamp(quantized, 0, POSITION_MAX_COORD - 1);
    }

    public static int packPositionHi(int x, int y, int z) {
        return (((x >>> 10) & 0x3FF) << 0)
             | (((y >>> 10) & 0x3FF) << 10)
             | (((z >>> 10) & 0x3FF) << 20);
    }

    public static int packPositionLo(int x, int y, int z) {
        return ((x & 0x3FF) << 0)
             | ((y & 0x3FF) << 10)
             | ((z & 0x3FF) << 20);
    }

    public static int packUV(float u, float v) {
        int u16 = (int) (Mth.clamp(u, 0.0F, 1.0F) * 65535.0F + 0.5F) & 0xFFFF;
        int v16 = (int) (Mth.clamp(v, 0.0F, 1.0F) * 65535.0F + 0.5F) & 0xFFFF;
        return (v16 << 16) | u16;
    }

    public static int packLightAndData(int light, int material, int section) {
        int block = light & 0xFF;
        int sky = (light >>> 16) & 0xFF;
        return (block & 0xFF)
             | ((sky & 0xFF) << 8)
             | ((material & 0xFF) << 16)
             | ((section & 0xFF) << 24);
    }

    public static float unpackCoord(int hi, int lo, int shift) {
        int raw = (((hi >>> shift) & 0x3FF) << 10) | ((lo >>> shift) & 0x3FF);
        return ((float) raw * POSITION_DEQUANT_SCALE) - MODEL_ORIGIN;
    }
}
