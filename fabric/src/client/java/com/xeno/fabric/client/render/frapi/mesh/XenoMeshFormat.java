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

import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
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
    public static final int HEADER_FACE_NORMAL = 1;
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

    private static final int CULL_FACE_SHIFT = 0;
    private static final int CULL_FACE_MASK = 0x7;
    private static final int NOMINAL_FACE_SHIFT = 3;
    private static final int NOMINAL_FACE_MASK = 0x7 << NOMINAL_FACE_SHIFT;
    private static final int CHUNK_LAYER_SHIFT = 6;
    private static final int CHUNK_LAYER_MASK = 0x7 << CHUNK_LAYER_SHIFT;
    private static final int ITEM_RENDER_SHIFT = 9;
    private static final int ITEM_RENDER_MASK = 0x3 << ITEM_RENDER_SHIFT;
    private static final int ITEM_GLINT_SHIFT = 11;
    private static final int ITEM_GLINT_MASK = 0x3 << ITEM_GLINT_SHIFT;
    private static final int ITEM_SPECIAL_SHIFT = 13;
    private static final int ITEM_SPECIAL_MASK = 0x3 << ITEM_SPECIAL_SHIFT;
    private static final int FOIL_TYPE_SHIFT = 15;
    private static final int FOIL_TYPE_MASK = 0x3 << FOIL_TYPE_SHIFT;
    private static final int EMISSIVE_SHIFT = 17;
    private static final int EMISSIVE_MASK = 0x1 << EMISSIVE_SHIFT;
    private static final int AO_SHIFT = 18;
    private static final int AO_MASK = 0x3 << AO_SHIFT;
    private static final int SHADE_MODE_SHIFT = 20;
    private static final int SHADE_MODE_MASK = 0x1 << SHADE_MODE_SHIFT;
    private static final int ANIMATED_SHIFT = 21;
    private static final int ANIMATED_MASK = 0x1 << ANIMATED_SHIFT;
    private static final int SHADE_OVERRIDE_SHIFT = 22;
    private static final int SHADE_OVERRIDE_MASK = 0x7 << SHADE_OVERRIDE_SHIFT;
    private static final int ATLAS_SHIFT = 25;
    private static final int ATLAS_MASK = 0x3 << ATLAS_SHIFT;
    private static final int LIGHT_FACE_SHIFT = 27;
    private static final int LIGHT_FACE_MASK = 0x7 << LIGHT_FACE_SHIFT;
    private static final int GEOMETRY_VALID_SHIFT = 30;
    private static final int GEOMETRY_VALID_MASK = 0x1 << GEOMETRY_VALID_SHIFT;

    private static final ChunkSectionLayer[] CHUNK_LAYERS = ChunkSectionLayer.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final QuadAtlas[] QUAD_ATLASES = QuadAtlas.values();

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
        return val == 0 || val > CHUNK_LAYERS.length ? ChunkSectionLayer.CUTOUT : CHUNK_LAYERS[val - 1];
    }

    public static int setChunkLayer(int bits, ChunkSectionLayer layer) {
        int val = layer == null ? 0 : layer.ordinal() + 1;
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
        return (bits & SHADE_MODE_MASK) != 0 ? ShadeMode.VANILLA : ShadeMode.ENHANCED;
    }

    public static int setShadeMode(int bits, ShadeMode mode) {
        int val = mode == ShadeMode.VANILLA ? 1 : 0;
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

    public static @Nullable QuadAtlas getAtlas(int bits) {
        int val = (bits & ATLAS_MASK) >>> ATLAS_SHIFT;
        return val == 0 || val > QUAD_ATLASES.length ? null : QUAD_ATLASES[val - 1];
    }

    public static int setAtlas(int bits, @Nullable QuadAtlas atlas) {
        int val = atlas == null ? 0 : atlas.ordinal() + 1;
        return (bits & ~ATLAS_MASK) | (val << ATLAS_SHIFT);
    }

    public static @Nullable Direction getLightFace(int bits) {
        int val = (bits & LIGHT_FACE_MASK) >>> LIGHT_FACE_SHIFT;
        return val == 0 ? null : DIRECTIONS[val - 1];
    }

    public static int setLightFace(int bits, @Nullable Direction face) {
        int val = face == null ? 0 : face.ordinal() + 1;
        return (bits & ~LIGHT_FACE_MASK) | (val << LIGHT_FACE_SHIFT);
    }

    public static boolean isGeometryValid(int bits) {
        return (bits & GEOMETRY_VALID_MASK) != 0;
    }

    public static int setGeometryValid(int bits, boolean valid) {
        return valid ? (bits | GEOMETRY_VALID_MASK) : (bits & ~GEOMETRY_VALID_MASK);
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

    public static Direction.Axis longestAxis(float normalX, float normalY, float normalZ) {
        Direction.Axis result = Direction.Axis.Y;
        float longest = Math.abs(normalY);
        float a = Math.abs(normalX);
        if (a > longest) {
            result = Direction.Axis.X;
            longest = a;
        }
        return Math.abs(normalZ) > longest ? Direction.Axis.Z : result;
    }

    public static Direction lightFace(float normalX, float normalY, float normalZ) {
        return switch (longestAxis(normalX, normalY, normalZ)) {
            case X -> normalX > 0 ? Direction.EAST : Direction.WEST;
            case Y -> normalY > 0 ? Direction.UP : Direction.DOWN;
            case Z -> normalZ > 0 ? Direction.SOUTH : Direction.NORTH;
        };
    }

    public static boolean isQuadParallelToFace(Direction face,
                                               float x0, float y0, float z0,
                                               float x1, float y1, float z1,
                                               float x2, float y2, float z2,
                                               float x3, float y3, float z3) {
        Direction.Axis axis = face.getAxis();
        float val0 = switch (axis) {
            case X -> x0;
            case Y -> y0;
            case Z -> z0;
        };
        float val1 = switch (axis) {
            case X -> x1;
            case Y -> y1;
            case Z -> z1;
        };
        float val2 = switch (axis) {
            case X -> x2;
            case Y -> y2;
            case Z -> z2;
        };
        float val3 = switch (axis) {
            case X -> x3;
            case Y -> y3;
            case Z -> z3;
        };
        return Mth.equal(val0, val1) && Mth.equal(val0, val2) && Mth.equal(val0, val3);
    }

    public static void computeFaceNormal(Vector3f saveTo, @Nullable Direction nominalFace,
                                         float x0, float y0, float z0,
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

        if (nominalFace != null && isQuadParallelToFace(nominalFace, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3)) {
            float dot = nx * nominalFace.getStepX() + ny * nominalFace.getStepY() + nz * nominalFace.getStepZ();
            if (dot > 0.0F) {
                saveTo.set(nominalFace.getStepX(), nominalFace.getStepY(), nominalFace.getStepZ());
                return;
            } else if (dot < 0.0F) {
                saveTo.set(-nominalFace.getStepX(), -nominalFace.getStepY(), -nominalFace.getStepZ());
                return;
            }
        }

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
