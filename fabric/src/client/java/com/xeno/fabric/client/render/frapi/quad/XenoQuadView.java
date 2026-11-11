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
    private static final ThreadLocal<Vector3f> SCRATCH_POS = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> SCRATCH_NORM = ThreadLocal.withInitial(Vector3f::new);

    protected int[] data;
    protected int baseIndex;

    private final Vector3f cachedFaceNormal = new Vector3f();
    private boolean faceNormalComputed = false;

    public void load(int[] data, int baseIndex) {
        this.data = data;
        this.baseIndex = baseIndex;
        int bits = data[baseIndex + XenoMeshFormat.HEADER_BITS];
        if (XenoMeshFormat.isGeometryValid(bits)) {
            int packed = data[baseIndex + XenoMeshFormat.HEADER_FACE_NORMAL];
            this.cachedFaceNormal.set(
                XenoMeshFormat.unpackNormalX(packed),
                XenoMeshFormat.unpackNormalY(packed),
                XenoMeshFormat.unpackNormalZ(packed)
            );
            this.faceNormalComputed = true;
        } else {
            this.faceNormalComputed = false;
        }
    }

    public void unload() {
        this.data = null;
        this.faceNormalComputed = false;
    }

    public int[] data() {
        return this.data;
    }

    public int baseIndex() {
        return this.baseIndex;
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

    public void computeGeometry() {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        if (!XenoMeshFormat.isGeometryValid(bits)) {
            Direction nominal = XenoMeshFormat.getNominalFace(bits);
            XenoMeshFormat.computeFaceNormal(
                this.cachedFaceNormal, nominal,
                x(0), y(0), z(0),
                x(1), y(1), z(1),
                x(2), y(2), z(2),
                x(3), y(3), z(3)
            );
            this.faceNormalComputed = true;
            this.data[this.baseIndex + XenoMeshFormat.HEADER_FACE_NORMAL] =
                XenoMeshFormat.packNormal(this.cachedFaceNormal.x(), this.cachedFaceNormal.y(), this.cachedFaceNormal.z());
            Direction lightFace = XenoMeshFormat.lightFace(this.cachedFaceNormal.x(), this.cachedFaceNormal.y(), this.cachedFaceNormal.z());
            bits = XenoMeshFormat.setLightFace(bits, lightFace);
            bits = XenoMeshFormat.setGeometryValid(bits, true);
            this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = bits;
        }
    }

    @Override
    public @NonNull Vector3fc faceNormal() {
        if (!this.faceNormalComputed) {
            int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
            if (XenoMeshFormat.isGeometryValid(bits)) {
                int packed = this.data[this.baseIndex + XenoMeshFormat.HEADER_FACE_NORMAL];
                this.cachedFaceNormal.set(
                    XenoMeshFormat.unpackNormalX(packed),
                    XenoMeshFormat.unpackNormalY(packed),
                    XenoMeshFormat.unpackNormalZ(packed)
                );
                this.faceNormalComputed = true;
            } else {
                computeGeometry();
            }
        }
        return this.cachedFaceNormal;
    }

    @Override
    public @NonNull Direction lightFace() {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        Direction cached = XenoMeshFormat.getLightFace(bits);
        if (cached != null) {
            return cached;
        }
        computeGeometry();
        Direction face = XenoMeshFormat.getLightFace(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
        return face != null ? face : Direction.UP;
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
        QuadAtlas stored = XenoMeshFormat.getAtlas(this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS]);
        return stored != null ? stored : QuadAtlas.BLOCK;
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
            vertexConsumer.addVertex(x(i), y(i), z(i), color(i), u(i), v(i), overlayCoords, lightmap(i), nx, ny, nz);
        }
    }

    @Override
    public void buffer(int overlayCoords, PoseStack.@NonNull Pose pose, @NonNull VertexConsumer vertexConsumer) {
        Vector3fc fn = faceNormal();
        Vector3f tempPos = SCRATCH_POS.get();
        Vector3f tempNorm = SCRATCH_NORM.get();

        for (int i = 0; i < 4; i++) {
            pose.pose().transformPosition(x(i), y(i), z(i), tempPos);
            float nx = hasNormal(i) ? normalX(i) : fn.x();
            float ny = hasNormal(i) ? normalY(i) : fn.y();
            float nz = hasNormal(i) ? normalZ(i) : fn.z();
            pose.normal().transform(nx, ny, nz, tempNorm);

            vertexConsumer.addVertex(
                tempPos.x(), tempPos.y(), tempPos.z(),
                color(i), u(i), v(i), overlayCoords, lightmap(i),
                tempNorm.x(), tempNorm.y(), tempNorm.z()
            );
        }
    }

    protected void invalidateGeometry() {
        this.faceNormalComputed = false;
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setGeometryValid(bits, false);
    }
}
