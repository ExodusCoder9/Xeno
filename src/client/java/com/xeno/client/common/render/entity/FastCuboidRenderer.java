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

        float[] tx = new float[8];
        float[] ty = new float[8];
        float[] tz = new float[8];

        tx[0] = x0; ty[0] = y0; tz[0] = z0;
        tx[1] = x0 + vxx; ty[1] = y0 + vxy; tz[1] = z0 + vxz;
        tx[2] = tx[1] + vyx; ty[2] = ty[1] + vyy; tz[2] = tz[1] + vyz;
        tx[3] = x0 + vyx; ty[3] = y0 + vyy; tz[3] = z0 + vyz;
        
        tx[4] = x0 + vzx; ty[4] = y0 + vzy; tz[4] = z0 + vzz;
        tx[5] = tx[1] + vzx; ty[5] = ty[1] + vzy; tz[5] = tz[1] + vzz;
        tx[6] = tx[2] + vzx; ty[6] = ty[2] + vzy; tz[6] = tz[2] + vzz;
        tx[7] = tx[3] + vzx; ty[7] = ty[3] + vzy; tz[7] = tz[3] + vzz;

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
                    
                    MemoryAccess.putFloat(ptr + 0L, tx[cIdx]);
                    MemoryAccess.putFloat(ptr + 4L, ty[cIdx]);
                    MemoryAccess.putFloat(ptr + 8L, tz[cIdx]);
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
                    builder.addVertex(tx[cIdx], ty[cIdx], tz[cIdx], color, v.u(), v.v(), overlay, light, normal.x(), normal.y(), normal.z());
                }
            }
        }
    }
}
