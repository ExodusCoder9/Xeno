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

package com.xeno.client.common.render.culling;

import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;

public final class XenoBranchlessFrustum {
    public static final int INSIDE = FrustumIntersection.INSIDE;
    public static final int INTERSECT = FrustumIntersection.INTERSECT;
    public static final int OUTSIDE = FrustumIntersection.OUTSIDE;
    private static final float DEFAULT_SECTION_EXTENT = 8.0f;
    private float p0_nx, p0_ny, p0_nz, p0_d;
    private float p0_absNx, p0_absNy, p0_absNz, p0_chunkD;
    private float p1_nx, p1_ny, p1_nz, p1_d;
    private float p1_absNx, p1_absNy, p1_absNz, p1_chunkD;
    private float p2_nx, p2_ny, p2_nz, p2_d;
    private float p2_absNx, p2_absNy, p2_absNz, p2_chunkD;
    private float p3_nx, p3_ny, p3_nz, p3_d;
    private float p3_absNx, p3_absNy, p3_absNz, p3_chunkD;
    private float p4_nx, p4_ny, p4_nz, p4_d;
    private float p4_absNx, p4_absNy, p4_absNz, p4_chunkD;
    private float p5_nx, p5_ny, p5_nz, p5_d;
    private float p5_absNx, p5_absNy, p5_absNz, p5_chunkD;
    private double camX;
    private double camY;
    private double camZ;
    public XenoBranchlessFrustum() {
    }

    public void set(Matrix4fc matrix, double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        float x0 = matrix.m03() + matrix.m00();
        float y0 = matrix.m13() + matrix.m10();
        float z0 = matrix.m23() + matrix.m20();
        float w0 = matrix.m33() + matrix.m30();
        float invL0 = 1.0f / (float) Math.sqrt(x0 * x0 + y0 * y0 + z0 * z0);
        this.p0_nx = x0 * invL0;
        this.p0_ny = y0 * invL0;
        this.p0_nz = z0 * invL0;
        this.p0_d = w0 * invL0;
        this.p0_absNx = Math.abs(this.p0_nx);
        this.p0_absNy = Math.abs(this.p0_ny);
        this.p0_absNz = Math.abs(this.p0_nz);
        this.p0_chunkD = this.p0_d + DEFAULT_SECTION_EXTENT * (this.p0_absNx + this.p0_absNy + this.p0_absNz);
        float x1 = matrix.m03() - matrix.m00();
        float y1 = matrix.m13() - matrix.m10();
        float z1 = matrix.m23() - matrix.m20();
        float w1 = matrix.m33() - matrix.m30();
        float invL1 = 1.0f / (float) Math.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
        this.p1_nx = x1 * invL1;
        this.p1_ny = y1 * invL1;
        this.p1_nz = z1 * invL1;
        this.p1_d = w1 * invL1;
        this.p1_absNx = Math.abs(this.p1_nx);
        this.p1_absNy = Math.abs(this.p1_ny);
        this.p1_absNz = Math.abs(this.p1_nz);
        this.p1_chunkD = this.p1_d + DEFAULT_SECTION_EXTENT * (this.p1_absNx + this.p1_absNy + this.p1_absNz);
        float x2 = matrix.m03() + matrix.m01();
        float y2 = matrix.m13() + matrix.m11();
        float z2 = matrix.m23() + matrix.m21();
        float w2 = matrix.m33() + matrix.m31();
        float invL2 = 1.0f / (float) Math.sqrt(x2 * x2 + y2 * y2 + z2 * z2);
        this.p2_nx = x2 * invL2;
        this.p2_ny = y2 * invL2;
        this.p2_nz = z2 * invL2;
        this.p2_d = w2 * invL2;
        this.p2_absNx = Math.abs(this.p2_nx);
        this.p2_absNy = Math.abs(this.p2_ny);
        this.p2_absNz = Math.abs(this.p2_nz);
        this.p2_chunkD = this.p2_d + DEFAULT_SECTION_EXTENT * (this.p2_absNx + this.p2_absNy + this.p2_absNz);
        float x3 = matrix.m03() - matrix.m01();
        float y3 = matrix.m13() - matrix.m11();
        float z3 = matrix.m23() - matrix.m21();
        float w3 = matrix.m33() - matrix.m31();
        float invL3 = 1.0f / (float) Math.sqrt(x3 * x3 + y3 * y3 + z3 * z3);
        this.p3_nx = x3 * invL3;
        this.p3_ny = y3 * invL3;
        this.p3_nz = z3 * invL3;
        this.p3_d = w3 * invL3;
        this.p3_absNx = Math.abs(this.p3_nx);
        this.p3_absNy = Math.abs(this.p3_ny);
        this.p3_absNz = Math.abs(this.p3_nz);
        this.p3_chunkD = this.p3_d + DEFAULT_SECTION_EXTENT * (this.p3_absNx + this.p3_absNy + this.p3_absNz);
        float x4 = matrix.m03() + matrix.m02();
        float y4 = matrix.m13() + matrix.m12();
        float z4 = matrix.m23() + matrix.m22();
        float w4 = matrix.m33() + matrix.m32();
        float invL4 = 1.0f / (float) Math.sqrt(x4 * x4 + y4 * y4 + z4 * z4);
        this.p4_nx = x4 * invL4;
        this.p4_ny = y4 * invL4;
        this.p4_nz = z4 * invL4;
        this.p4_d = w4 * invL4;
        this.p4_absNx = Math.abs(this.p4_nx);
        this.p4_absNy = Math.abs(this.p4_ny);
        this.p4_absNz = Math.abs(this.p4_nz);
        this.p4_chunkD = this.p4_d + DEFAULT_SECTION_EXTENT * (this.p4_absNx + this.p4_absNy + this.p4_absNz);
        float x5 = matrix.m03() - matrix.m02();
        float y5 = matrix.m13() - matrix.m12();
        float z5 = matrix.m23() - matrix.m22();
        float w5 = matrix.m33() - matrix.m32();
        float invL5 = 1.0f / (float) Math.sqrt(x5 * x5 + y5 * y5 + z5 * z5);
        this.p5_nx = x5 * invL5;
        this.p5_ny = y5 * invL5;
        this.p5_nz = z5 * invL5;
        this.p5_d = w5 * invL5;
        this.p5_absNx = Math.abs(this.p5_nx);
        this.p5_absNy = Math.abs(this.p5_ny);
        this.p5_absNz = Math.abs(this.p5_nz);
        this.p5_chunkD = this.p5_d + DEFAULT_SECTION_EXTENT * (this.p5_absNx + this.p5_absNy + this.p5_absNz);
    }

    public void setCameraPos(double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }


    public boolean isVisibleRel(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        final float cx = (minX + maxX) * 0.5f;
        final float cy = (minY + maxY) * 0.5f;
        final float cz = (minZ + maxZ) * 0.5f;
        final float ex = (maxX - minX) * 0.5f;
        final float ey = (maxY - minY) * 0.5f;
        final float ez = (maxZ - minZ) * 0.5f;
        final float v0 = this.p0_nx * cx + this.p0_ny * cy + this.p0_nz * cz + this.p0_absNx * ex + this.p0_absNy * ey + this.p0_absNz * ez + this.p0_d + 0.0f;
        final float v1 = this.p1_nx * cx + this.p1_ny * cy + this.p1_nz * cz + this.p1_absNx * ex + this.p1_absNy * ey + this.p1_absNz * ez + this.p1_d + 0.0f;
        final float v2 = this.p2_nx * cx + this.p2_ny * cy + this.p2_nz * cz + this.p2_absNx * ex + this.p2_absNy * ey + this.p2_absNz * ez + this.p2_d + 0.0f;
        final float v3 = this.p3_nx * cx + this.p3_ny * cy + this.p3_nz * cz + this.p3_absNx * ex + this.p3_absNy * ey + this.p3_absNz * ez + this.p3_d + 0.0f;
        final float v4 = this.p4_nx * cx + this.p4_ny * cy + this.p4_nz * cz + this.p4_absNx * ex + this.p4_absNy * ey + this.p4_absNz * ez + this.p4_d + 0.0f;
        final float v5 = this.p5_nx * cx + this.p5_ny * cy + this.p5_nz * cz + this.p5_absNx * ex + this.p5_absNy * ey + this.p5_absNz * ez + this.p5_d + 0.0f;
        final int culledMask = (Float.floatToRawIntBits(v0) |
                                Float.floatToRawIntBits(v1) |
                                Float.floatToRawIntBits(v2) |
                                Float.floatToRawIntBits(v3) |
                                Float.floatToRawIntBits(v4) |
                                Float.floatToRawIntBits(v5)) & 0x80000000;
        return culledMask == 0;
    }

    public boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return isVisibleRel(
                (float) (minX - this.camX),
                (float) (minY - this.camY),
                (float) (minZ - this.camZ),
                (float) (maxX - this.camX),
                (float) (maxY - this.camY),
                (float) (maxZ - this.camZ)
        );
    }

    public boolean isVisible(AABB aabb) {
        return isVisibleRel(
                (float) (aabb.minX - this.camX),
                (float) (aabb.minY - this.camY),
                (float) (aabb.minZ - this.camZ),
                (float) (aabb.maxX - this.camX),
                (float) (aabb.maxY - this.camY),
                (float) (aabb.maxZ - this.camZ)
        );
    }

    public boolean isSectionVisibleRel(float relCenterX, float relCenterY, float relCenterZ) {
        final float v0 = this.p0_nx * relCenterX + this.p0_ny * relCenterY + this.p0_nz * relCenterZ + this.p0_chunkD + 0.0f;
        final float v1 = this.p1_nx * relCenterX + this.p1_ny * relCenterY + this.p1_nz * relCenterZ + this.p1_chunkD + 0.0f;
        final float v2 = this.p2_nx * relCenterX + this.p2_ny * relCenterY + this.p2_nz * relCenterZ + this.p2_chunkD + 0.0f;
        final float v3 = this.p3_nx * relCenterX + this.p3_ny * relCenterY + this.p3_nz * relCenterZ + this.p3_chunkD + 0.0f;
        final float v4 = this.p4_nx * relCenterX + this.p4_ny * relCenterY + this.p4_nz * relCenterZ + this.p4_chunkD + 0.0f;
        final float v5 = this.p5_nx * relCenterX + this.p5_ny * relCenterY + this.p5_nz * relCenterZ + this.p5_chunkD + 0.0f;
        final int culledMask = (Float.floatToRawIntBits(v0) |
                                Float.floatToRawIntBits(v1) |
                                Float.floatToRawIntBits(v2) |
                                Float.floatToRawIntBits(v3) |
                                Float.floatToRawIntBits(v4) |
                                Float.floatToRawIntBits(v5)) & 0x80000000;

        return culledMask == 0;
    }

    public int cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        final float relMinX = (float) (minX - this.camX);
        final float relMinY = (float) (minY - this.camY);
        final float relMinZ = (float) (minZ - this.camZ);
        final float relMaxX = (float) (maxX - this.camX);
        final float relMaxY = (float) (maxY - this.camY);
        final float relMaxZ = (float) (maxZ - this.camZ);
        final float cx = (relMinX + relMaxX) * 0.5f;
        final float cy = (relMinY + relMaxY) * 0.5f;
        final float cz = (relMinZ + relMaxZ) * 0.5f;
        final float ex = (relMaxX - relMinX) * 0.5f;
        final float ey = (relMaxY - relMinY) * 0.5f;
        final float ez = (relMaxZ - relMinZ) * 0.5f;
        final float vp0 = this.p0_nx * cx + this.p0_ny * cy + this.p0_nz * cz + this.p0_absNx * ex + this.p0_absNy * ey + this.p0_absNz * ez + this.p0_d + 0.0f;
        final float vp1 = this.p1_nx * cx + this.p1_ny * cy + this.p1_nz * cz + this.p1_absNx * ex + this.p1_absNy * ey + this.p1_absNz * ez + this.p1_d + 0.0f;
        final float vp2 = this.p2_nx * cx + this.p2_ny * cy + this.p2_nz * cz + this.p2_absNx * ex + this.p2_absNy * ey + this.p2_absNz * ez + this.p2_d + 0.0f;
        final float vp3 = this.p3_nx * cx + this.p3_ny * cy + this.p3_nz * cz + this.p3_absNx * ex + this.p3_absNy * ey + this.p3_absNz * ez + this.p3_d + 0.0f;
        final float vp4 = this.p4_nx * cx + this.p4_ny * cy + this.p4_nz * cz + this.p4_absNx * ex + this.p4_absNy * ey + this.p4_absNz * ez + this.p4_d + 0.0f;
        final float vp5 = this.p5_nx * cx + this.p5_ny * cy + this.p5_nz * cz + this.p5_absNx * ex + this.p5_absNy * ey + this.p5_absNz * ez + this.p5_d + 0.0f;
        final int outsideMask = (Float.floatToRawIntBits(vp0) |
                                 Float.floatToRawIntBits(vp1) |
                                 Float.floatToRawIntBits(vp2) |
                                 Float.floatToRawIntBits(vp3) |
                                 Float.floatToRawIntBits(vp4) |
                                 Float.floatToRawIntBits(vp5)) & 0x80000000;

        if (outsideMask != 0) {
            return OUTSIDE;
        }
        final float vn0 = this.p0_nx * cx + this.p0_ny * cy + this.p0_nz * cz - (this.p0_absNx * ex + this.p0_absNy * ey + this.p0_absNz * ez) + this.p0_d + 0.0f;
        final float vn1 = this.p1_nx * cx + this.p1_ny * cy + this.p1_nz * cz - (this.p1_absNx * ex + this.p1_absNy * ey + this.p1_absNz * ez) + this.p1_d + 0.0f;
        final float vn2 = this.p2_nx * cx + this.p2_ny * cy + this.p2_nz * cz - (this.p2_absNx * ex + this.p2_absNy * ey + this.p2_absNz * ez) + this.p2_d + 0.0f;
        final float vn3 = this.p3_nx * cx + this.p3_ny * cy + this.p3_nz * cz - (this.p3_absNx * ex + this.p3_absNy * ey + this.p3_absNz * ez) + this.p3_d + 0.0f;
        final float vn4 = this.p4_nx * cx + this.p4_ny * cy + this.p4_nz * cz - (this.p4_absNx * ex + this.p4_absNy * ey + this.p4_absNz * ez) + this.p4_d + 0.0f;
        final float vn5 = this.p5_nx * cx + this.p5_ny * cy + this.p5_nz * cz - (this.p5_absNx * ex + this.p5_absNy * ey + this.p5_absNz * ez) + this.p5_d + 0.0f;
        final int intersectMask = (Float.floatToRawIntBits(vn0) |
                                   Float.floatToRawIntBits(vn1) |
                                   Float.floatToRawIntBits(vn2) |
                                   Float.floatToRawIntBits(vn3) |
                                   Float.floatToRawIntBits(vn4) |
                                   Float.floatToRawIntBits(vn5)) & 0x80000000;
        return intersectMask != 0 ? INTERSECT : INSIDE;
    }
    public double getCamX() {
        return this.camX;
    }
    public double getCamY() {
        return this.camY;
    }
    public double getCamZ() {
        return this.camZ;
    }
}
