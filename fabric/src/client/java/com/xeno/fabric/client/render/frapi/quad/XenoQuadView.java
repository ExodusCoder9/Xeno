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

package com.xeno.fabric.client.render.frapi.quad;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.fabric.client.render.frapi.mesh.XenoItemRenderTypes;
import com.xeno.fabric.client.render.frapi.mesh.XenoMeshFormat;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class XenoQuadView implements QuadView {
    protected int[] data;
    protected int baseIndex;

    private final Vector3f cachedFaceNormal = new Vector3f();
    private boolean faceNormalComputed = false;

    public void load(int[] data, int baseIndex) {
        this.data = data;
        this.baseIndex = baseIndex;
        this.faceNormalComputed = false;
    }

    public int[] data() {
        return this.data;
    }

    private int vertexOffset(int vertexIndex) {
        return this.baseIndex + XenoMeshFormat.HEADER_STRIDE + vertexIndex * XenoMeshFormat.VERTEX_STRIDE;
    }

    @Override
    public float x(int vertexIndex) {
        return Float.intBitsToFloat(this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_X]);
    }

    @Override
    public float y(int vertexIndex) {
        return Float.intBitsToFloat(this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_Y]);
    }

    @Override
    public float z(int vertexIndex) {
        return Float.intBitsToFloat(this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_Z]);
    }

    @Override
    public float posByIndex(int vertexIndex, int coordinateIndex) {
        return Float.intBitsToFloat(this.data[vertexOffset(vertexIndex) + coordinateIndex]);
    }

    @Override
    public @NonNull Vector3f copyPos(int vertexIndex, @Nullable Vector3f target) {
        if (target == null) target = new Vector3f();
        target.set(x(vertexIndex), y(vertexIndex), z(vertexIndex));
        return target;
    }

    @Override
    public int color(int vertexIndex) {
        return this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_COLOR];
    }

    @Override
    public float u(int vertexIndex) {
        return Float.intBitsToFloat(this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_U]);
    }

    @Override
    public float v(int vertexIndex) {
        return Float.intBitsToFloat(this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_V]);
    }

    @Override
    public @NonNull Vector2f copyUv(int vertexIndex, @Nullable Vector2f target) {
        if (target == null) target = new Vector2f();
        target.set(u(vertexIndex), v(vertexIndex));
        return target;
    }

    @Override
    public int lightmap(int vertexIndex) {
        return this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_LIGHT];
    }

    @Override
    public boolean hasNormal(int vertexIndex) {
        return XenoMeshFormat.hasNormal(this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_NORMAL]);
    }

    @Override
    public float normalX(int vertexIndex) {
        int packed = this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_NORMAL];
        return XenoMeshFormat.hasNormal(packed) ? XenoMeshFormat.unpackNormalX(packed) : Float.NaN;
    }

    @Override
    public float normalY(int vertexIndex) {
        int packed = this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_NORMAL];
        return XenoMeshFormat.hasNormal(packed) ? XenoMeshFormat.unpackNormalY(packed) : Float.NaN;
    }

    @Override
    public float normalZ(int vertexIndex) {
        int packed = this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_NORMAL];
        return XenoMeshFormat.hasNormal(packed) ? XenoMeshFormat.unpackNormalZ(packed) : Float.NaN;
    }

    @Override
    public @Nullable Vector3f copyNormal(int vertexIndex, @Nullable Vector3f target) {
        if (!hasNormal(vertexIndex)) return null;
        if (target == null) target = new Vector3f();
        target.set(normalX(vertexIndex), normalY(vertexIndex), normalZ(vertexIndex));
        return target;
    }

    @Override
    public @NonNull Vector3fc faceNormal() {
        if (!this.faceNormalComputed) {
            XenoMeshFormat.computeFaceNormal(
                this.cachedFaceNormal,
                x(0), y(0), z(0),
                x(1), y(1), z(1),
                x(2), y(2), z(2),
                x(3), y(3), z(3)
            );
            this.faceNormalComputed = true;
        }
        return this.cachedFaceNormal;
    }

    @Override
    public @NonNull Direction lightFace() {
        return Direction.getApproximateNearest(faceNormal().x(), faceNormal().y(), faceNormal().z());
    }

    @Override
    public @Nullable Direction nominalFace() {
        return XenoMeshFormat.getNominalFace(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public @Nullable Direction cullFace() {
        return XenoMeshFormat.getCullFace(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public @NonNull QuadAtlas atlas() {
        return XenoItemRenderTypes.getAtlasForRenderType(itemRenderType());
    }

    @Override
    public @NonNull ChunkSectionLayer chunkLayer() {
        return XenoMeshFormat.getChunkLayer(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public @NonNull RenderType itemRenderType() {
        return XenoItemRenderTypes.toItemRenderType(
            XenoMeshFormat.getItemRenderType(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS])
        );
    }

    @Override
    public @NonNull RenderType itemGlintRenderType() {
        return XenoItemRenderTypes.toItemGlintRenderType(
            XenoMeshFormat.getItemGlintRenderType(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS])
        );
    }

    @Override
    public @NonNull RenderType itemGlintSpecialRenderType() {
        return XenoItemRenderTypes.toItemGlintSpecialRenderType(
            XenoMeshFormat.getItemGlintSpecialRenderType(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS])
        );
    }

    @Override
    public boolean emissive() {
        return XenoMeshFormat.isEmissive(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public @Nullable Direction shadeDirectionOverride() {
        return XenoMeshFormat.getShadeDirectionOverride(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public @NonNull TriState ambientOcclusion() {
        return XenoMeshFormat.getAmbientOcclusion(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public ItemStackRenderState.@Nullable FoilType foilType() {
        return XenoMeshFormat.getFoilType(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public @NonNull ShadeMode shadeMode() {
        return XenoMeshFormat.getShadeMode(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public boolean animated() {
        return XenoMeshFormat.isAnimated(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
    }

    @Override
    public int tintIndex() {
        return this.data[this.baseIndex + XenoMeshFormat.HEADER_TINT_INDEX];
    }

    @Override
    public int tag() {
        return this.data[this.baseIndex + XenoMeshFormat.HEADER_TAG];
    }

    @Override
    public void buffer(int overlayCoords, @NonNull VertexConsumer vertexConsumer) {
        Vector3fc fn = faceNormal();
        for (int i = 0; i < 4; i++) {
            float nx = hasNormal(i) ? normalX(i) : fn.x();
            float ny = hasNormal(i) ? normalY(i) : fn.y();
            float nz = hasNormal(i) ? normalZ(i) : fn.z();
            vertexConsumer.addVertex(x(i), y(i), z(i));
            vertexConsumer.setColor(color(i));
            vertexConsumer.setUv(u(i), v(i));
            vertexConsumer.setOverlay(overlayCoords);
            vertexConsumer.setLight(lightmap(i));
            vertexConsumer.setNormal(nx, ny, nz);
        }
    }

    @Override
    public void buffer(int overlayCoords, PoseStack.@NonNull Pose pose, @NonNull VertexConsumer vertexConsumer) {
        Vector3fc fn = faceNormal();
        Vector3f tempPos = new Vector3f();
        Vector3f tempNorm = new Vector3f();

        for (int i = 0; i < 4; i++) {
            pose.pose().transformPosition(x(i), y(i), z(i), tempPos);
            float nx = hasNormal(i) ? normalX(i) : fn.x();
            float ny = hasNormal(i) ? normalY(i) : fn.y();
            float nz = hasNormal(i) ? normalZ(i) : fn.z();
            pose.normal().transform(nx, ny, nz, tempNorm);

            vertexConsumer.addVertex(tempPos.x(), tempPos.y(), tempPos.z());
            vertexConsumer.setColor(color(i));
            vertexConsumer.setUv(u(i), v(i));
            vertexConsumer.setOverlay(overlayCoords);
            vertexConsumer.setLight(lightmap(i));
            vertexConsumer.setNormal(tempNorm.x(), tempNorm.y(), tempNorm.z());
        }
    }

    protected void invalidateFaceNormal() {
        this.faceNormalComputed = false;
    }
}
