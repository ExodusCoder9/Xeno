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

package com.xeno.fabric.client.render.frapi.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

public final class XenoItemDecalGenerator implements VertexConsumer {
    private VertexConsumer delegate;
    private Matrix4fc cameraInversePose;
    private Matrix3fc normalInversePose;
    private float textureScale;

    private final Vector3f worldPos = new Vector3f();
    private final Vector3f normal = new Vector3f();

    private float x;
    private float y;
    private float z;

    public void prepare(VertexConsumer delegate, Matrix4fc cameraInversePose, Matrix3fc normalInversePose, float textureScale) {
        this.delegate = delegate;
        this.cameraInversePose = cameraInversePose;
        this.normalInversePose = normalInversePose;
        this.textureScale = textureScale;
    }

    public void clear() {
        this.delegate = null;
        this.cameraInversePose = null;
        this.normalInversePose = null;
    }

    @Override
    public @NonNull VertexConsumer addVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setColor(int r, int g, int b, int a) {
        this.delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setColor(int color) {
        this.delegate.setColor(color);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv(float u, float v) {
        this.delegate.setUv(u, v);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public @NonNull VertexConsumer setNormal(float x, float y, float z) {
        this.delegate.setNormal(x, y, z);
        Vector3f norm = this.normalInversePose.transform(x, y, z, this.normal);
        Direction direction = Direction.getApproximateNearest(norm.x(), norm.y(), norm.z());
        Vector3f wPos = this.cameraInversePose.transformPosition(this.x, this.y, this.z, this.worldPos);
        wPos.rotateY(Mth.PI);
        wPos.rotateX(-Mth.HALF_PI);
        wPos.rotate(direction.getRotation());
        this.delegate.setUv3(-wPos.x() * this.textureScale, -wPos.y() * this.textureScale);
        return this;
    }

    @Override
    public @NonNull VertexConsumer setLineWidth(float width) {
        this.delegate.setLineWidth(width);
        return this;
    }
}
