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

import com.xeno.client.common.memory.MemoryAccess;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;

public final class XenoCuboidData {
    public final float[] cornerX;
    public final float[] cornerY;
    public final float[] cornerZ;
    public final int cornerCount;

    public final int faceCount;
    public final int[] faceIndices;
    public final long[] faceUVs;
    public final float[] faceU;
    public final float[] faceV;
    public final Vector3fc[] faceNormals;

    public XenoCuboidData(ModelPart.Cube cube) {
        ModelPart.Polygon[] polygons = cube.polygons;
        this.faceCount = polygons != null ? polygons.length : 0;

        float[] tempCornerX = new float[this.faceCount * 4];
        float[] tempCornerY = new float[this.faceCount * 4];
        float[] tempCornerZ = new float[this.faceCount * 4];
        int corners = 0;

        this.faceIndices = new int[this.faceCount * 4];
        this.faceUVs = new long[this.faceCount * 4];
        this.faceU = new float[this.faceCount * 4];
        this.faceV = new float[this.faceCount * 4];
        this.faceNormals = new Vector3fc[this.faceCount];

        int vertexIndex = 0;
        for (int f = 0; f < this.faceCount; f++) {
            ModelPart.Polygon poly = polygons[f];
            this.faceNormals[f] = poly.normal();
            ModelPart.Vertex[] verts = poly.vertices();
            for (int i = 0; i < verts.length; i++) {
                ModelPart.Vertex v = verts[i];
                float wx = v.worldX();
                float wy = v.worldY();
                float wz = v.worldZ();

                int matchedCorner = -1;
                for (int c = 0; c < corners; c++) {
                    if (Float.floatToRawIntBits(tempCornerX[c]) == Float.floatToRawIntBits(wx)
                     && Float.floatToRawIntBits(tempCornerY[c]) == Float.floatToRawIntBits(wy)
                     && Float.floatToRawIntBits(tempCornerZ[c]) == Float.floatToRawIntBits(wz)) {
                        matchedCorner = c;
                        break;
                    }
                }

                if (matchedCorner == -1) {
                    matchedCorner = corners++;
                    tempCornerX[matchedCorner] = wx;
                    tempCornerY[matchedCorner] = wy;
                    tempCornerZ[matchedCorner] = wz;
                }

                this.faceIndices[vertexIndex] = matchedCorner;
                this.faceU[vertexIndex] = v.u();
                this.faceV[vertexIndex] = v.v();
                this.faceUVs[vertexIndex] = MemoryAccess.packFloats(v.u(), v.v());
                vertexIndex++;
            }
        }

        this.cornerCount = corners;
        this.cornerX = new float[corners];
        this.cornerY = new float[corners];
        this.cornerZ = new float[corners];
        System.arraycopy(tempCornerX, 0, this.cornerX, 0, corners);
        System.arraycopy(tempCornerY, 0, this.cornerY, 0, corners);
        System.arraycopy(tempCornerZ, 0, this.cornerZ, 0, corners);
    }
}
