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

package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.client.common.render.entity.FastCube;
import com.xeno.client.common.render.entity.FastCuboidRenderer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import net.minecraft.core.Direction;

@Mixin(ModelPart.Cube.class)
public class CubeMixin implements FastCube {
    @Unique
    private Vector3f[] xeno$corners;

    @Unique
    private int[] xeno$vertexIndices;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(int xTexOffs, int yTexOffs, float x, float y, float z, float w, float h, float d, float dx, float dy, float dz, boolean mirror, float tu, float tv, Set<Direction> dirs, CallbackInfo ci) {
        float minX = x - dx;
        float minY = y - dy;
        float minZ = z - dz;
        float maxX = x + w + dx;
        float maxY = y + h + dy;
        float maxZ = z + d + dz;

        if (mirror) {
            float tmp = maxX;
            maxX = minX;
            minX = tmp;
        }

        minX /= 16.0f;
        minY /= 16.0f;
        minZ /= 16.0f;
        maxX /= 16.0f;
        maxY /= 16.0f;
        maxZ /= 16.0f;

        this.xeno$corners = new Vector3f[] {
            new Vector3f(minX, minY, minZ),
            new Vector3f(maxX, minY, minZ),
            new Vector3f(maxX, maxY, minZ),
            new Vector3f(minX, maxY, minZ),
            new Vector3f(minX, minY, maxZ),
            new Vector3f(maxX, minY, maxZ),
            new Vector3f(maxX, maxY, maxZ),
            new Vector3f(minX, maxY, maxZ)
        };

        ModelPart.Polygon[] polygons = ((ModelPart.Cube)(Object)this).polygons;
        int totalVerts = 0;
        for (ModelPart.Polygon p : polygons) {
            totalVerts += p.vertices().length;
        }

        this.xeno$vertexIndices = new int[totalVerts];
        int idx = 0;
        for (ModelPart.Polygon p : polygons) {
            for (ModelPart.Vertex v : p.vertices()) {
                this.xeno$vertexIndices[idx++] = xeno$findCorner(v.worldX(), v.worldY(), v.worldZ());
            }
        }
    }

    @Unique
    private int xeno$findCorner(float vx, float vy, float vz) {
        for (int i = 0; i < 8; i++) {
            Vector3f c = this.xeno$corners[i];
            if (Math.abs(c.x() - vx) < 0.0001f && Math.abs(c.y() - vy) < 0.0001f && Math.abs(c.z() - vz) < 0.0001f) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public Vector3f[] getCorners() {
        return this.xeno$corners;
    }

    @Override
    public int[] getVertexIndices() {
        return this.xeno$vertexIndices;
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void onCompile(PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, int color, CallbackInfo ci) {
        FastCuboidRenderer.renderCube((ModelPart.Cube)(Object)this, pose, builder, light, overlay, color);
        ci.cancel();
    }
}
