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
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.common.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.xeno.client.common.memory.MemoryAccess;
import java.nio.ByteOrder;
import net.minecraft.util.ARGB;

public class FastCuboidRenderer {
    private static final Vector3f SCRATCH_NORMAL = new Vector3f();
    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    public static void renderCube(ModelPart.Cube cube, PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, int color) {
        FastCube fastCube = (FastCube) cube;
        Vector3f[] localCorners = fastCube.getCorners();
        int[] indices = fastCube.getVertexIndices();

        Matrix4f matrix = pose.pose();
        
        float m00 = matrix.m00(), m01 = matrix.m01(), m02 = matrix.m02(), m03 = matrix.m03();
        float m10 = matrix.m10(), m11 = matrix.m11(), m12 = matrix.m12(), m13 = matrix.m13();
        float m20 = matrix.m20(), m21 = matrix.m21(), m22 = matrix.m22(), m23 = matrix.m23();
        float m30 = matrix.m30(), m31 = matrix.m31(), m32 = matrix.m32(), m33 = matrix.m33();

        Vector3f min = localCorners[0];
        Vector3f max = localCorners[6];

        float dx = max.x() - min.x();
        float dy = max.y() - min.y();
        float dz = max.z() - min.z();

        float x0 = min.x() * m00 + min.y() * m10 + min.z() * m20 + m30;
        float y0 = min.x() * m01 + min.y() * m11 + min.z() * m21 + m31;
        float z0 = min.x() * m02 + min.y() * m12 + min.z() * m22 + m32;

        float vxx = dx * m00, vxy = dx * m01, vxz = dx * m02;
        float vyx = dy * m10, vyy = dy * m11, vyz = dy * m12;
        float vzx = dz * m20, vzy = dz * m21, vzz = dz * m22;

        float tx0 = x0, ty0 = y0, tz0 = z0;
        float tx1 = x0 + vxx, ty1 = y0 + vxy, tz1 = z0 + vxz;
        float tx2 = tx1 + vyx, ty2 = ty1 + vyy, tz2 = tz1 + vyz;
        float tx3 = x0 + vyx, ty3 = y0 + vyy, tz3 = z0 + vyz;
        
        float tx4 = x0 + vzx, ty4 = y0 + vzy, tz4 = z0 + vzz;
        float tx5 = tx1 + vzx, ty5 = ty1 + vzy, tz5 = tz1 + vzz;
        float tx6 = tx2 + vzx, ty6 = ty2 + vzy, tz6 = tz2 + vzz;
        float tx7 = tx3 + vzx, ty7 = ty3 + vzy, tz7 = tz3 + vzz;

        ModelPart.Polygon[] polygons = cube.polygons;
        int vIdx = 0;
        
        if (builder instanceof XenoBufferWriter xbw && xbw.xeno$isEntityFormat()) {
            int totalVerts = 0;
            for (ModelPart.Polygon poly : polygons) totalVerts += poly.vertices().length;
            
            long ptr = xbw.xeno$reserveVertices(totalVerts);
            long packedOverlayLight = ((long) overlay & 0xFFFFFFFFL) | (((long) light & 0xFFFFFFFFL) << 32);
            int abgr = ARGB.toABGR(color);
            int packedColor = IS_LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr);
            
            for (ModelPart.Polygon poly : polygons) {
                Vector3f normal = pose.transformNormal(poly.normal(), SCRATCH_NORMAL);
                byte nx = (byte) ((int) (Math.clamp(normal.x(), -1.0f, 1.0f) * 127.0f) & 0xFF);
                byte ny = (byte) ((int) (Math.clamp(normal.y(), -1.0f, 1.0f) * 127.0f) & 0xFF);
                byte nz = (byte) ((int) (Math.clamp(normal.z(), -1.0f, 1.0f) * 127.0f) & 0xFF);
                int packedNormal = (nx & 0xFF) | ((ny & 0xFF) << 8) | ((nz & 0xFF) << 16);
                
                for (ModelPart.Vertex v : poly.vertices()) {
                    int cIdx = indices[vIdx++];
                    float vx = 0, vy = 0, vz = 0;
                    switch (cIdx) {
                        case 0: vx = tx0; vy = ty0; vz = tz0; break;
                        case 1: vx = tx1; vy = ty1; vz = tz1; break;
                        case 2: vx = tx2; vy = ty2; vz = tz2; break;
                        case 3: vx = tx3; vy = ty3; vz = tz3; break;
                        case 4: vx = tx4; vy = ty4; vz = tz4; break;
                        case 5: vx = tx5; vy = ty5; vz = tz5; break;
                        case 6: vx = tx6; vy = ty6; vz = tz6; break;
                        case 7: vx = tx7; vy = ty7; vz = tz7; break;
                    }
                    
                    MemoryAccess.putFloat(ptr + 0L, vx);
                    MemoryAccess.putFloat(ptr + 4L, vy);
                    MemoryAccess.putFloat(ptr + 8L, vz);
                    MemoryAccess.putInt(ptr + 12L, packedColor);
                    
                    if (IS_LITTLE_ENDIAN) {
                        long packedUV = ((long) Float.floatToRawIntBits(v.u()) & 0xFFFFFFFFL) | 
                                        (((long) Float.floatToRawIntBits(v.v()) & 0xFFFFFFFFL) << 32);
                        MemoryAccess.putLong(ptr + 16L, packedUV);
                        MemoryAccess.putLong(ptr + 24L, packedOverlayLight);
                    } else {
                        MemoryAccess.putFloat(ptr + 16L, v.u());
                        MemoryAccess.putFloat(ptr + 20L, v.v());
                        MemoryAccess.putInt(ptr + 24L, overlay);
                        MemoryAccess.putInt(ptr + 28L, light);
                    }
                    
                    MemoryAccess.putInt(ptr + 32L, packedNormal);
                    ptr += 36L;
                }
            }
        } else {
            for (ModelPart.Polygon poly : polygons) {
                Vector3f normal = pose.transformNormal(poly.normal(), SCRATCH_NORMAL);
                for (ModelPart.Vertex v : poly.vertices()) {
                    int cIdx = indices[vIdx++];
                    float vx = 0, vy = 0, vz = 0;
                    switch (cIdx) {
                        case 0: vx = tx0; vy = ty0; vz = tz0; break;
                        case 1: vx = tx1; vy = ty1; vz = tz1; break;
                        case 2: vx = tx2; vy = ty2; vz = tz2; break;
                        case 3: vx = tx3; vy = ty3; vz = tz3; break;
                        case 4: vx = tx4; vy = ty4; vz = tz4; break;
                        case 5: vx = tx5; vy = ty5; vz = tz5; break;
                        case 6: vx = tx6; vy = ty6; vz = tz6; break;
                        case 7: vx = tx7; vy = ty7; vz = tz7; break;
                    }
                    builder.addVertex(vx, vy, vz, color, v.u(), v.v(), overlay, light, normal.x(), normal.y(), normal.z());
                }
            }
        }
    }
}
