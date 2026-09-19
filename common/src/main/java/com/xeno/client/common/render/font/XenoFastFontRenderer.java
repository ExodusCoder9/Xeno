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

package com.xeno.client.common.render.font;

import com.xeno.client.common.memory.MemoryAccess;
import com.xeno.client.common.render.entity.XenoBufferWriter;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;

import java.nio.ByteOrder;

public final class XenoFastFontRenderer {

    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private XenoFastFontRenderer() {
    }

    public static void renderGlyph(
        XenoBufferWriter writer,
        Matrix4fc pose,
        float left, float right, float up, float down,
        float shearTop, float shearBottom,
        boolean italic, boolean bold,
        float x, float y, float z,
        float u0, float u1, float v0, float v1,
        int color,
        int packedLightCoords
    ) {
        float minX = x + left;
        float maxX = x + right;
        float minY = y + up;
        float maxY = y + down;
        float shearY0 = italic ? shearTop : 0.0F;
        float shearY1 = italic ? shearBottom : 0.0F;
        float thickness = bold ? 0.1F : 0.0F;

        float px0 = minX + shearY0 - thickness;
        float py0 = minY - thickness;

        float px1 = minX + shearY1 - thickness;
        float py1 = maxY + thickness;

        float px2 = maxX + shearY1 + thickness;
        float py2 = maxY + thickness;

        float px3 = maxX + shearY0 + thickness;
        float py3 = minY - thickness;

        emitQuad(writer, pose, px0, py0, px1, py1, px2, py2, px3, py3, z, u0, u1, v0, v1, color, packedLightCoords);
    }

    public static void renderEffect(
        XenoBufferWriter writer,
        Matrix4fc pose,
        float x0, float y0, float x1, float y1,
        float offset, float z,
        float u0, float u1, float v0, float v1,
        int color,
        int packedLightCoords
    ) {
        float px0 = x0 + offset;
        float py0 = y1 + offset;

        float px1 = x1 + offset;
        float py1 = y1 + offset;

        float px2 = x1 + offset;
        float py2 = y0 + offset;

        float px3 = x0 + offset;
        float py3 = y0 + offset;

        emitQuad(writer, pose, px0, py0, px1, py1, px2, py2, px3, py3, z, u0, u1, v0, v1, color, packedLightCoords);
    }

    private static void emitQuad(
        XenoBufferWriter writer,
        Matrix4fc pose,
        float px0, float py0,
        float px1, float py1,
        float px2, float py2,
        float px3, float py3,
        float z,
        float u0, float u1,
        float v0, float v1,
        int color,
        int light
    ) {
        long ptr = writer.xeno$reserveVertices(4);
        int abgr = ARGB.toABGR(color);
        int packedColor = IS_LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr);

        float m00 = pose.m00(), m01 = pose.m01(), m02 = pose.m02();
        float m10 = pose.m10(), m11 = pose.m11(), m12 = pose.m12();
        float m20 = pose.m20(), m21 = pose.m21(), m22 = pose.m22();
        float m30 = pose.m30(), m31 = pose.m31(), m32 = pose.m32();

        float baseZ_X = m20 * z + m30;
        float baseZ_Y = m21 * z + m31;
        float baseZ_Z = m22 * z + m32;

        writeVertex(ptr + 0L,  m00 * px0 + m10 * py0 + baseZ_X, m01 * px0 + m11 * py0 + baseZ_Y, m02 * px0 + m12 * py0 + baseZ_Z, u0, v0, light, packedColor);
        writeVertex(ptr + 28L, m00 * px1 + m10 * py1 + baseZ_X, m01 * px1 + m11 * py1 + baseZ_Y, m02 * px1 + m12 * py1 + baseZ_Z, u0, v1, light, packedColor);
        writeVertex(ptr + 56L, m00 * px2 + m10 * py2 + baseZ_X, m01 * px2 + m11 * py2 + baseZ_Y, m02 * px2 + m12 * py2 + baseZ_Z, u1, v1, light, packedColor);
        writeVertex(ptr + 84L, m00 * px3 + m10 * py3 + baseZ_X, m01 * px3 + m11 * py3 + baseZ_Y, m02 * px3 + m12 * py3 + baseZ_Z, u1, v0, light, packedColor);
    }

    private static void writeVertex(long ptr, float vx, float vy, float vz, float u, float v, int light, int packedColor) {
        if (IS_LITTLE_ENDIAN) {
            MemoryAccess.putLong(ptr + 0L, MemoryAccess.packFloats(vx, vy));
            MemoryAccess.putLong(ptr + 8L, MemoryAccess.packInts(Float.floatToRawIntBits(vz), Float.floatToRawIntBits(u)));
            MemoryAccess.putLong(ptr + 16L, MemoryAccess.packInts(Float.floatToRawIntBits(v), light));
            MemoryAccess.putInt(ptr + 24L, packedColor);
        } else {
            MemoryAccess.putFloat(ptr + 0L, vx);
            MemoryAccess.putFloat(ptr + 4L, vy);
            MemoryAccess.putFloat(ptr + 8L, vz);
            MemoryAccess.putFloat(ptr + 12L, u);
            MemoryAccess.putFloat(ptr + 16L, v);
            MemoryAccess.putInt(ptr + 20L, light);
            MemoryAccess.putInt(ptr + 24L, packedColor);
        }
    }
}
