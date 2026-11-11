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

package com.xeno.fabric.client.render.frapi.mesh;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class XenoMeshFormat {
    private XenoMeshFormat() {}

    public static final int HEADER_BITS = 0;
    public static final int HEADER_TINT_INDEX = 2;
    public static final int HEADER_TAG = 3;
    public static final int HEADER_STRIDE = 4;

    public static final int OFFSET_X = 0;
    public static final int OFFSET_Y = 1;
    public static final int OFFSET_Z = 2;
    public static final int OFFSET_COLOR = 3;
    public static final int OFFSET_U = 4;
    public static final int OFFSET_V = 5;
    public static final int OFFSET_LIGHT = 6;
    public static final int OFFSET_NORMAL = 7;
    public static final int VERTEX_STRIDE = 8;

    public static final int QUAD_STRIDE = VERTEX_STRIDE * 4;
    public static final int TOTAL_STRIDE = HEADER_STRIDE + QUAD_STRIDE;

    // Bit offsets in HEADER_BITS
    private static final int CULL_FACE_SHIFT = 0;           // 3 bits (0=none, 1-6=Direction)
    private static final int CULL_FACE_MASK = 0x7;
    private static final int NOMINAL_FACE_SHIFT = 3;        // 3 bits
    private static final int NOMINAL_FACE_MASK = 0x7 << NOMINAL_FACE_SHIFT;
    private static final int CHUNK_LAYER_SHIFT = 6;         // 3 bits
    private static final int CHUNK_LAYER_MASK = 0x7 << CHUNK_LAYER_SHIFT;
    private static final int ITEM_RENDER_SHIFT = 9;         // 2 bits
    private static final int ITEM_RENDER_MASK = 0x3 << ITEM_RENDER_SHIFT;
    private static final int ITEM_GLINT_SHIFT = 11;         // 2 bits
    private static final int ITEM_GLINT_MASK = 0x3 << ITEM_GLINT_SHIFT;
    private static final int ITEM_SPECIAL_SHIFT = 13;       // 2 bits
    private static final int ITEM_SPECIAL_MASK = 0x3 << ITEM_SPECIAL_SHIFT;
    private static final int FOIL_TYPE_SHIFT = 15;          // 2 bits (0=none, 1=std, 2=special)
    private static final int FOIL_TYPE_MASK = 0x3 << FOIL_TYPE_SHIFT;
    private static final int EMISSIVE_SHIFT = 17;           // 1 bit
    private static final int EMISSIVE_MASK = 0x1 << EMISSIVE_SHIFT;
    private static final int AO_SHIFT = 18;                 // 2 bits (0=FALSE, 1=DEFAULT, 2=TRUE)
    private static final int AO_MASK = 0x3 << AO_SHIFT;
    private static final int SHADE_MODE_SHIFT = 20;         // 1 bit (0=VANILLA, 1=ENHANCED)
    private static final int SHADE_MODE_MASK = 0x1 << SHADE_MODE_SHIFT;
    private static final int ANIMATED_SHIFT = 21;           // 1 bit
    private static final int ANIMATED_MASK = 0x1 << ANIMATED_SHIFT;
    private static final int SHADE_OVERRIDE_SHIFT = 22;     // 3 bits
    private static final int SHADE_OVERRIDE_MASK = 0x7 << SHADE_OVERRIDE_SHIFT;

    private static final ChunkSectionLayer[] CHUNK_LAYERS = ChunkSectionLayer.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    public static @Nullable Direction getCullFace(int bits) {
        int val = (bits & CULL_FACE_MASK) >>> CULL_FACE_SHIFT;
        return val == 0 ? null : DIRECTIONS[val - 1];
    }

    public static int setCullFace(int bits, @Nullable Direction face) {
        int val = face == null ? 0 : face.ordinal() + 1;
        return (bits & ~CULL_FACE_MASK) | (val << CULL_FACE_SHIFT);
    }

    public static @Nullable Direction getNominalFace(int bits) {
        int val = (bits & NOMINAL_FACE_MASK) >>> NOMINAL_FACE_SHIFT;
        return val == 0 ? null : DIRECTIONS[val - 1];
    }

    public static int setNominalFace(int bits, @Nullable Direction face) {
        int val = face == null ? 0 : face.ordinal() + 1;
        return (bits & ~NOMINAL_FACE_MASK) | (val << NOMINAL_FACE_SHIFT);
    }

    public static ChunkSectionLayer getChunkLayer(int bits) {
        int val = (bits & CHUNK_LAYER_MASK) >>> CHUNK_LAYER_SHIFT;
        return val < CHUNK_LAYERS.length ? CHUNK_LAYERS[val] : ChunkSectionLayer.SOLID;
    }

    public static int setChunkLayer(int bits, ChunkSectionLayer layer) {
        int val = layer == null ? 0 : layer.ordinal();
        return (bits & ~CHUNK_LAYER_MASK) | (val << CHUNK_LAYER_SHIFT);
    }

    public static int getItemRenderType(int bits) {
        return (bits & ITEM_RENDER_MASK) >>> ITEM_RENDER_SHIFT;
    }

    public static int setItemRenderType(int bits, int index) {
        return (bits & ~ITEM_RENDER_MASK) | ((index & 0x3) << ITEM_RENDER_SHIFT);
    }

    public static int getItemGlintRenderType(int bits) {
        return (bits & ITEM_GLINT_MASK) >>> ITEM_GLINT_SHIFT;
    }

    public static int setItemGlintRenderType(int bits, int index) {
        return (bits & ~ITEM_GLINT_MASK) | ((index & 0x3) << ITEM_GLINT_SHIFT);
    }

    public static int getItemGlintSpecialRenderType(int bits) {
        return (bits & ITEM_SPECIAL_MASK) >>> ITEM_SPECIAL_SHIFT;
    }

    public static int setItemGlintSpecialRenderType(int bits, int index) {
        return (bits & ~ITEM_SPECIAL_MASK) | ((index & 0x3) << ITEM_SPECIAL_SHIFT);
    }

    public static ItemStackRenderState.@Nullable FoilType getFoilType(int bits) {
        int val = (bits & FOIL_TYPE_MASK) >>> FOIL_TYPE_SHIFT;
        return switch (val) {
            case 1 -> ItemStackRenderState.FoilType.STANDARD;
            case 2 -> ItemStackRenderState.FoilType.SPECIAL;
            default -> null;
        };
    }

    public static int setFoilType(int bits, ItemStackRenderState.@Nullable FoilType foilType) {
        int val = foilType == null ? 0 : switch (foilType) {
            case NONE -> 0;
            case STANDARD -> 1;
            case SPECIAL -> 2;
        };
        return (bits & ~FOIL_TYPE_MASK) | (val << FOIL_TYPE_SHIFT);
    }

    public static boolean isEmissive(int bits) {
        return (bits & EMISSIVE_MASK) != 0;
    }

    public static int setEmissive(int bits, boolean emissive) {
        return emissive ? (bits | EMISSIVE_MASK) : (bits & ~EMISSIVE_MASK);
    }

    public static TriState getAmbientOcclusion(int bits) {
        int val = (bits & AO_MASK) >>> AO_SHIFT;
        return switch (val) {
            case 0 -> TriState.FALSE;
            case 2 -> TriState.TRUE;
            default -> TriState.DEFAULT;
        };
    }

    public static int setAmbientOcclusion(int bits, TriState ao) {
        int val = ao == null ? 1 : switch (ao) {
            case FALSE -> 0;
            case DEFAULT -> 1;
            case TRUE -> 2;
        };
        return (bits & ~AO_MASK) | (val << AO_SHIFT);
    }

    public static ShadeMode getShadeMode(int bits) {
        return (bits & SHADE_MODE_MASK) != 0 ? ShadeMode.ENHANCED : ShadeMode.VANILLA;
    }

    public static int setShadeMode(int bits, ShadeMode mode) {
        int val = mode == ShadeMode.ENHANCED ? 1 : 0;
        return (bits & ~SHADE_MODE_MASK) | (val << SHADE_MODE_SHIFT);
    }

    public static boolean isAnimated(int bits) {
        return (bits & ANIMATED_MASK) != 0;
    }

    public static int setAnimated(int bits, boolean animated) {
        return animated ? (bits | ANIMATED_MASK) : (bits & ~ANIMATED_MASK);
    }

    public static @Nullable Direction getShadeDirectionOverride(int bits) {
        int val = (bits & SHADE_OVERRIDE_MASK) >>> SHADE_OVERRIDE_SHIFT;
        return val == 0 ? null : DIRECTIONS[val - 1];
    }

    public static int setShadeDirectionOverride(int bits, @Nullable Direction dir) {
        int val = dir == null ? 0 : dir.ordinal() + 1;
        return (bits & ~SHADE_OVERRIDE_MASK) | (val << SHADE_OVERRIDE_SHIFT);
    }

    private static final float NORMAL_PACK = 127.0f;
    private static final float NORMAL_UNPACK = 1.0f / NORMAL_PACK;

    public static int packNormal(float x, float y, float z) {
        x = Mth.clamp(x, -1.0F, 1.0F);
        y = Mth.clamp(y, -1.0F, 1.0F);
        z = Mth.clamp(z, -1.0F, 1.0F);
        return ((int) (x * NORMAL_PACK) & 0xFF)
            | (((int) (y * NORMAL_PACK) & 0xFF) << 8)
            | (((int) (z * NORMAL_PACK) & 0xFF) << 16)
            | 0xFF000000;
    }

    public static boolean hasNormal(int packedNormal) {
        return (packedNormal & 0xFF000000) != 0;
    }

    public static float unpackNormalX(int packedNormal) {
        return ((byte) (packedNormal & 0xFF)) * NORMAL_UNPACK;
    }

    public static float unpackNormalY(int packedNormal) {
        return ((byte) ((packedNormal >>> 8) & 0xFF)) * NORMAL_UNPACK;
    }

    public static float unpackNormalZ(int packedNormal) {
        return ((byte) ((packedNormal >>> 16) & 0xFF)) * NORMAL_UNPACK;
    }

    public static void computeFaceNormal(Vector3f saveTo, float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float x3, float y3, float z3) {
        float dx0 = x2 - x0;
        float dy0 = y2 - y0;
        float dz0 = z2 - z0;
        float dx1 = x3 - x1;
        float dy1 = y3 - y1;
        float dz1 = z3 - z1;

        float nx = dy0 * dz1 - dz0 * dy1;
        float ny = dz0 * dx1 - dx0 * dz1;
        float nz = dx0 * dy1 - dy0 * dx1;

        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-6F) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }
        saveTo.set(nx, ny, nz);
    }
}
