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

package com.xeno.client.common.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import com.xeno.client.common.memory.MemoryAccess;
import java.nio.ByteOrder;

public final class FastCuboidRenderer {

    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static final class ScratchBuffers {
        final float[] transformedX = new float[32];
        final float[] transformedY = new float[32];
        final float[] transformedZ = new float[32];
        final Vector3f scratchNormal = new Vector3f();
    }

    private static final ThreadLocal<ScratchBuffers> SCRATCH = ThreadLocal.withInitial(ScratchBuffers::new);

    private FastCuboidRenderer() {
    }

    public static void renderCube(ModelPart.Cube cube, PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, int color) {
        XenoCuboidData data;
        if (cube instanceof XenoCuboidHolder holder) {
            data = holder.xeno$getCuboidData();
        } else {
            data = new XenoCuboidData(cube);
        }
        renderCuboid(data, pose, builder, light, overlay, color);
    }

    public static void renderCuboid(XenoCuboidData data, PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, int color) {
        int faceCount = data.faceCount;
        if (faceCount == 0) {
            return;
        }

        Matrix4f matrix = pose.pose();
        float m00 = matrix.m00(), m01 = matrix.m01(), m02 = matrix.m02(), m03 = matrix.m03();
        float m10 = matrix.m10(), m11 = matrix.m11(), m12 = matrix.m12(), m13 = matrix.m13();
        float m20 = matrix.m20(), m21 = matrix.m21(), m22 = matrix.m22(), m23 = matrix.m23();
        float m30 = matrix.m30(), m31 = matrix.m31(), m32 = matrix.m32(), m33 = matrix.m33();

        ScratchBuffers scratch = SCRATCH.get();
        float[] transformedX = scratch.transformedX;
        float[] transformedY = scratch.transformedY;
        float[] transformedZ = scratch.transformedZ;

        int cornerCount = data.cornerCount;
        if (cornerCount > transformedX.length) {
            scratch = new ScratchBuffers();
            transformedX = new float[cornerCount];
            transformedY = new float[cornerCount];
            transformedZ = new float[cornerCount];
        }

        float[] cornerX = data.cornerX;
        float[] cornerY = data.cornerY;
        float[] cornerZ = data.cornerZ;

        for (int c = 0; c < cornerCount; c++) {
            float x = cornerX[c];
            float y = cornerY[c];
            float z = cornerZ[c];

            transformedX[c] = m00 * x + m10 * y + m20 * z + m30;
            transformedY[c] = m01 * x + m11 * y + m21 * z + m31;
            transformedZ[c] = m02 * x + m12 * y + m22 * z + m32;
        }

        if (builder instanceof XenoBufferWriter xbw && xbw.xeno$isEntityFormat()) {
            int totalVerts = faceCount * 4;
            long ptr = xbw.xeno$reserveVertices(totalVerts);
            long packedOverlayLight = ((long) overlay & 0xFFFFFFFFL) | (((long) light & 0xFFFFFFFFL) << 32);
            int abgr = ARGB.toABGR(color);
            int packedColor = IS_LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr);

            int[] faceIndices = data.faceIndices;
            long[] faceUVs = data.faceUVs;
            Vector3fc[] faceNormals = data.faceNormals;
            Vector3f scratchNormal = scratch.scratchNormal;

            for (int f = 0; f < faceCount; f++) {
                Vector3f normal = pose.transformNormal(faceNormals[f], scratchNormal);
                byte nx = (byte) ((int) (Math.clamp(normal.x(), -1.0f, 1.0f) * 127.0f) & 0xFF);
                byte ny = (byte) ((int) (Math.clamp(normal.y(), -1.0f, 1.0f) * 127.0f) & 0xFF);
                byte nz = (byte) ((int) (Math.clamp(normal.z(), -1.0f, 1.0f) * 127.0f) & 0xFF);
                int packedNormal = (nx & 0xFF) | ((ny & 0xFF) << 8) | ((nz & 0xFF) << 16);

                int faceBase = f * 4;
                for (int i = 0; i < 4; i++) {
                    int vertexIndex = faceBase + i;
                    int corner = faceIndices[vertexIndex];
                    float vx = transformedX[corner];
                    float vy = transformedY[corner];
                    float vz = transformedZ[corner];
                    long uv = faceUVs[vertexIndex];

                    if (IS_LITTLE_ENDIAN) {
                        MemoryAccess.putLong(ptr + 0L, MemoryAccess.packFloats(vx, vy));
                        MemoryAccess.putLong(ptr + 8L, MemoryAccess.packInts(Float.floatToRawIntBits(vz), packedColor));
                        MemoryAccess.putLong(ptr + 16L, uv);
                        MemoryAccess.putLong(ptr + 24L, packedOverlayLight);
                    } else {
                        MemoryAccess.putFloat(ptr + 0L, vx);
                        MemoryAccess.putFloat(ptr + 4L, vy);
                        MemoryAccess.putFloat(ptr + 8L, vz);
                        MemoryAccess.putInt(ptr + 12L, packedColor);
                        MemoryAccess.putFloat(ptr + 16L, data.faceU[vertexIndex]);
                        MemoryAccess.putFloat(ptr + 20L, data.faceV[vertexIndex]);
                        MemoryAccess.putInt(ptr + 24L, overlay);
                        MemoryAccess.putInt(ptr + 28L, light);
                    }

                    MemoryAccess.putInt(ptr + 32L, packedNormal);
                    ptr += 36L;
                }
            }
        } else {
            int[] faceIndices = data.faceIndices;
            float[] faceU = data.faceU;
            float[] faceV = data.faceV;
            Vector3fc[] faceNormals = data.faceNormals;
            Vector3f scratchNormal = scratch.scratchNormal;

            for (int f = 0; f < faceCount; f++) {
                Vector3f normal = pose.transformNormal(faceNormals[f], scratchNormal);
                float nx = normal.x();
                float ny = normal.y();
                float nz = normal.z();

                int faceBase = f * 4;
                for (int i = 0; i < 4; i++) {
                    int vertexIndex = faceBase + i;
                    int corner = faceIndices[vertexIndex];
                    float vx = transformedX[corner];
                    float vy = transformedY[corner];
                    float vz = transformedZ[corner];
                    builder.addVertex(vx, vy, vz, color, faceU[vertexIndex], faceV[vertexIndex], overlay, light, nx, ny, nz);
                }
            }
        }
    }
}
