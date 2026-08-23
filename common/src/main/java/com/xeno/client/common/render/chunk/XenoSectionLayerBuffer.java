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

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xeno.client.common.memory.MemoryAccess;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

//Please ignore the deprecated usage and for removal errors if you are on an IDE , there might be a lot , it is meant to be this way to follow correct procedure for deprecation .

public final class XenoSectionLayerBuffer implements VertexConsumer {
    private static final int VERTEX_SIZE = 28;

    private static final long Q_COLOR0 = MemoryAccess.fieldOffset(QuadInstance.class, "color0");
    private static final long Q_COLOR1 = MemoryAccess.fieldOffset(QuadInstance.class, "color1");
    private static final long Q_COLOR2 = MemoryAccess.fieldOffset(QuadInstance.class, "color2");
    private static final long Q_COLOR3 = MemoryAccess.fieldOffset(QuadInstance.class, "color3");
    private static final long Q_LIGHT0 = MemoryAccess.fieldOffset(QuadInstance.class, "lightCoords0");
    private static final long Q_LIGHT1 = MemoryAccess.fieldOffset(QuadInstance.class, "lightCoords1");
    private static final long Q_LIGHT2 = MemoryAccess.fieldOffset(QuadInstance.class, "lightCoords2");
    private static final long Q_LIGHT3 = MemoryAccess.fieldOffset(QuadInstance.class, "lightCoords3");

    private final ByteBufferBuilder buffer;
    private final VertexFormat format;
    private final ChunkSectionLayer layer;
    private int vertexCount;

    public XenoSectionLayerBuffer(ByteBufferBuilder buffer, VertexFormat format, ChunkSectionLayer layer) {
        this.buffer = buffer;
        this.format = format;
        this.layer = layer;
    }

    public ChunkSectionLayer layer() {
        return this.layer;
    }

    public MeshData build() {
        if (this.vertexCount == 0) {
            return null;
        }
        ByteBufferBuilder.Result vertexBuffer = this.buffer.build();
        if (vertexBuffer == null) {
            return null;
        }
        int indexCount = PrimitiveTopology.QUADS.indexCount(this.vertexCount);
        IndexType indexType = IndexType.least(this.vertexCount);
        return new MeshData(vertexBuffer, new MeshData.DrawState(this.format, this.vertexCount, indexCount, PrimitiveTopology.QUADS, indexType));
    }

    void resetForReuse() {
        this.vertexCount = 0;
    }

    public void writeBlockQuad(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
        long ptr = this.buffer.reserve(VERTEX_SIZE * 4);
        int emission = quad.materialInfo().lightEmission();

        float u0 = unpackU(quad.packedUV0()); float v0 = unpackV(quad.packedUV0());
        float u1 = unpackU(quad.packedUV1()); float v1 = unpackV(quad.packedUV1());
        float u2 = unpackU(quad.packedUV2()); float v2 = unpackV(quad.packedUV2());
        float u3 = unpackU(quad.packedUV3()); float v3 = unpackV(quad.packedUV3());

        Vector3fc p0 = quad.position0();
        this.writeVertex(
                ptr,
                x + p0.x(), y + p0.y(), z + p0.z(),
                MemoryAccess.getInt(instance, Q_COLOR0),
                u0, v0,
                LightCoordsUtil.lightCoordsWithEmission(MemoryAccess.getInt(instance, Q_LIGHT0), emission)
        );
        Vector3fc p1 = quad.position1();
        this.writeVertex(
                ptr + VERTEX_SIZE,
                x + p1.x(), y + p1.y(), z + p1.z(),
                MemoryAccess.getInt(instance, Q_COLOR1),
                u1, v1,
                LightCoordsUtil.lightCoordsWithEmission(MemoryAccess.getInt(instance, Q_LIGHT1), emission)
        );
        Vector3fc p2 = quad.position2();
        this.writeVertex(
                ptr + VERTEX_SIZE * 2,
                x + p2.x(), y + p2.y(), z + p2.z(),
                MemoryAccess.getInt(instance, Q_COLOR2),
                u2, v2,
                LightCoordsUtil.lightCoordsWithEmission(MemoryAccess.getInt(instance, Q_LIGHT2), emission)
        );
        Vector3fc p3 = quad.position3();
        this.writeVertex(
                ptr + VERTEX_SIZE * 3,
                x + p3.x(), y + p3.y(), z + p3.z(),
                MemoryAccess.getInt(instance, Q_COLOR3),
                u3, v3,
                LightCoordsUtil.lightCoordsWithEmission(MemoryAccess.getInt(instance, Q_LIGHT3), emission)
        );
        this.vertexCount += 4;
    }

    private static float unpackU(long packedUv) {
        return Float.intBitsToFloat((int) (packedUv >> 32));
    }

    private static float unpackV(long packedUv) {
        return Float.intBitsToFloat((int) packedUv);
    }

    public void writeQuad(
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            int color, int lightCoords, boolean addBackFace
    ) {
        int vertexCount = addBackFace ? 8 : 4;
        long ptr = this.buffer.reserve(VERTEX_SIZE * vertexCount);

        this.writeVertex(ptr, x0, y0, z0, color, u0, v0, lightCoords);
        this.writeVertex(ptr + VERTEX_SIZE, x1, y1, z1, color, u1, v1, lightCoords);
        this.writeVertex(ptr + VERTEX_SIZE * 2, x2, y2, z2, color, u2, v2, lightCoords);
        this.writeVertex(ptr + VERTEX_SIZE * 3, x3, y3, z3, color, u3, v3, lightCoords);
        if (addBackFace) {
            this.writeVertex(ptr + VERTEX_SIZE * 4, x0, y0, z0, color, u0, v0, lightCoords);
            this.writeVertex(ptr + VERTEX_SIZE * 5, x3, y3, z3, color, u3, v3, lightCoords);
            this.writeVertex(ptr + VERTEX_SIZE * 6, x2, y2, z2, color, u2, v2, lightCoords);
            this.writeVertex(ptr + VERTEX_SIZE * 7, x1, y1, z1, color, u1, v1, lightCoords);
        }
        this.vertexCount += vertexCount;
    }

    private void writeVertex(long ptr, float px, float py, float pz, int color, float u, float v, int light) {
        MemoryAccess.putLong(ptr, MemoryAccess.packFloats(px, py));
        MemoryAccess.putLong(ptr + 8L, MemoryAccess.packInts(Float.floatToRawIntBits(pz), ARGB.toABGR(color)));
        MemoryAccess.putLong(ptr + 16L, MemoryAccess.packFloats(u, v));
        MemoryAccess.putInt(ptr + 24L, light);
    }

    @Override
    public @NonNull VertexConsumer addVertex(float x, float y, float z) {
        this.buffer.reserve(VERTEX_SIZE);
        this.vertexCount++;
        return this;
    }

    @Override
    public @NonNull VertexConsumer setColor(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public @NonNull VertexConsumer setColor(int color) {
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public @NonNull VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }

    @Override
    public @NonNull VertexConsumer setLineWidth(float width) {
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
        long ptr = this.buffer.reserve(VERTEX_SIZE);
        this.writeVertex(ptr, x, y, z, color, u, v, lightCoords);
        this.vertexCount++;
    }
}
