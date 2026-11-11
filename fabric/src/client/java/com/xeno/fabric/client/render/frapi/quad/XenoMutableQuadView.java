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

import com.xeno.fabric.client.render.frapi.mesh.XenoItemRenderTypes;
import com.xeno.fabric.client.render.frapi.mesh.XenoMeshFormat;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public class XenoMutableQuadView extends XenoQuadView implements MutableQuadView {

    private int vertexOffset(int vertexIndex) {
        return this.baseIndex + XenoMeshFormat.HEADER_STRIDE + vertexIndex * XenoMeshFormat.VERTEX_STRIDE;
    }

    @Override
    public @NonNull MutableQuadView pos(int vertexIndex, float x, float y, float z) {
        int off = vertexOffset(vertexIndex);
        this.data[off + XenoMeshFormat.OFFSET_X] = Float.floatToRawIntBits(x);
        this.data[off + XenoMeshFormat.OFFSET_Y] = Float.floatToRawIntBits(y);
        this.data[off + XenoMeshFormat.OFFSET_Z] = Float.floatToRawIntBits(z);
        invalidateFaceNormal();
        return this;
    }

    @Override
    public @NonNull MutableQuadView color(int vertexIndex, int color) {
        this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_COLOR] = color;
        return this;
    }

    @Override
    public @NonNull MutableQuadView uv(int vertexIndex, float u, float v) {
        int off = vertexOffset(vertexIndex);
        this.data[off + XenoMeshFormat.OFFSET_U] = Float.floatToRawIntBits(u);
        this.data[off + XenoMeshFormat.OFFSET_V] = Float.floatToRawIntBits(v);
        return this;
    }

    @Override
    public @NonNull MutableQuadView lightmap(int vertexIndex, int lightmap) {
        this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_LIGHT] = lightmap;
        return this;
    }

    @Override
    public @NonNull MutableQuadView normal(int vertexIndex, float x, float y, float z) {
        int packed = (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z))
            ? 0
            : XenoMeshFormat.packNormal(x, y, z);
        this.data[vertexOffset(vertexIndex) + XenoMeshFormat.OFFSET_NORMAL] = packed;
        return this;
    }

    @Override
    public @NonNull MutableQuadView nominalFace(@Nullable Direction face) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setNominalFace(bits, face);
        return this;
    }

    @Override
    public @NonNull MutableQuadView cullFace(@Nullable Direction face) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setCullFace(bits, face);
        return this;
    }

    @Override
    public @NonNull MutableQuadView atlas(@NonNull QuadAtlas quadAtlas) {
        return this;
    }

    @Override
    public @NonNull MutableQuadView chunkLayer(@NonNull ChunkSectionLayer layer) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setChunkLayer(bits, layer);
        return this;
    }

    @Override
    public @NonNull MutableQuadView itemRenderType(@NonNull RenderType renderType) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        int idx = XenoItemRenderTypes.toItemRenderTypeIndex(renderType);
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setItemRenderType(bits, idx);
        return this;
    }

    @Override
    public @NonNull MutableQuadView itemGlintRenderType(@NonNull RenderType renderType) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        int idx = XenoItemRenderTypes.toItemGlintRenderTypeIndex(renderType);
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setItemGlintRenderType(bits, idx);
        return this;
    }

    @Override
    public @NonNull MutableQuadView itemGlintSpecialRenderType(@NonNull RenderType renderType) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        int idx = XenoItemRenderTypes.toItemGlintSpecialRenderTypeIndex(renderType);
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setItemGlintSpecialRenderType(bits, idx);
        return this;
    }

    @Override
    public @NonNull MutableQuadView emissive(boolean emissive) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setEmissive(bits, emissive);
        return this;
    }

    @Override
    public @NonNull MutableQuadView shadeDirectionOverride(@Nullable Direction shadeDirection) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setShadeDirectionOverride(bits, shadeDirection);
        return this;
    }

    @Override
    public @NonNull MutableQuadView ambientOcclusion(@NonNull TriState ao) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setAmbientOcclusion(bits, ao);
        return this;
    }

    @Override
    public @NonNull MutableQuadView foilType(ItemStackRenderState.@Nullable FoilType foilType) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setFoilType(bits, foilType);
        return this;
    }

    @Override
    public @NonNull MutableQuadView shadeMode(@NonNull ShadeMode mode) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setShadeMode(bits, mode);
        return this;
    }

    @Override
    public @NonNull MutableQuadView animated(boolean animated) {
        int bits = this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS];
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = XenoMeshFormat.setAnimated(bits, animated);
        return this;
    }

    @Override
    public @NonNull MutableQuadView tintIndex(int tintIndex) {
        this.data[this.baseIndex + XenoMeshFormat.HEADER_TINT_INDEX] = tintIndex;
        return this;
    }

    @Override
    public @NonNull MutableQuadView tag(int tag) {
        this.data[this.baseIndex + XenoMeshFormat.HEADER_TAG] = tag;
        return this;
    }

    @Override
    public @NonNull MutableQuadView copyFrom(QuadView quad) {
        cullFace(quad.cullFace());
        nominalFace(quad.nominalFace());
        chunkLayer(quad.chunkLayer());
        itemRenderType(quad.itemRenderType());
        itemGlintRenderType(quad.itemGlintRenderType());
        itemGlintSpecialRenderType(quad.itemGlintSpecialRenderType());
        emissive(quad.emissive());
        shadeDirectionOverride(quad.shadeDirectionOverride());
        ambientOcclusion(quad.ambientOcclusion());
        foilType(quad.foilType());
        shadeMode(quad.shadeMode());
        animated(quad.animated());
        tintIndex(quad.tintIndex());
        tag(quad.tag());

        for (int i = 0; i < 4; i++) {
            pos(i, quad.x(i), quad.y(i), quad.z(i));
            color(i, quad.color(i));
            uv(i, quad.u(i), quad.v(i));
            lightmap(i, quad.lightmap(i));
            if (quad.hasNormal(i)) {
                normal(i, quad.normalX(i), quad.normalY(i), quad.normalZ(i));
            } else {
                normal(i, Float.NaN, Float.NaN, Float.NaN);
            }
        }
        return this;
    }

    @Override
    public @NonNull MutableQuadView fromBakedQuad(BakedQuad quad) {
        clear();
        cullFace(quad.direction());
        nominalFace(quad.direction());

        BakedQuad.MaterialInfo mat = quad.materialInfo();
        chunkLayer(mat.layer());
        itemRenderType(mat.itemRenderType());
        itemGlintRenderType(mat.itemGlintRenderType());
        itemGlintSpecialRenderType(mat.itemGlintSpecialRenderType());
        tintIndex(mat.tintIndex());
        shadeDirectionOverride(mat.shadeDirectionOverride());

        boolean isEmissive = mat.lightEmission() > 0;
        emissive(isEmissive);
        int defaultLight = isEmissive ? LightCoordsUtil.pack(mat.lightEmission(), mat.lightEmission()) : 0;

        for (int i = 0; i < 4; i++) {
            Vector3fc p = quad.position(i);
            pos(i, p.x(), p.y(), p.z());
            long packedUV = quad.packedUV(i);
            uv(i, UVPair.unpackU(packedUV), UVPair.unpackV(packedUV));
            color(i, 0xFFFFFFFF);
            lightmap(i, defaultLight);
            normal(i, Float.NaN, Float.NaN, Float.NaN);
        }
        return this;
    }

    @Override
    public @NonNull MutableQuadView clear() {
        Arrays.fill(this.data, this.baseIndex, this.baseIndex + XenoMeshFormat.TOTAL_STRIDE, 0);
        int bits = 0;
        bits = XenoMeshFormat.setChunkLayer(bits, ChunkSectionLayer.SOLID);
        bits = XenoMeshFormat.setAmbientOcclusion(bits, TriState.DEFAULT);
        this.data[this.baseIndex + XenoMeshFormat.HEADER_BITS] = bits;
        this.data[this.baseIndex + XenoMeshFormat.HEADER_TINT_INDEX] = -1;

        for (int i = 0; i < 4; i++) {
            color(i, 0xFFFFFFFF);
        }
        invalidateFaceNormal();
        return this;
    }
}
