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

import com.xeno.fabric.client.render.frapi.mesh.XenoMeshFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class XenoQuadEmitter extends XenoMutableQuadView implements QuadEmitter {
    protected static final QuadTransform NO_TRANSFORM = q -> true;

    private final Consumer<? super MutableQuadView> outputConsumer;
    private final ObjectArrayList<QuadTransform> transformStack = new ObjectArrayList<>();
    protected QuadTransform activeTransform = NO_TRANSFORM;

    private final QuadTransform stackTransform = q -> {
        for (int i = this.transformStack.size() - 1; i >= 0; i--) {
            if (!this.transformStack.get(i).transform(q)) {
                return false;
            }
        }
        return true;
    };

    public XenoQuadEmitter(@Nullable Consumer<? super MutableQuadView> outputConsumer) {
        this.outputConsumer = outputConsumer;
        this.data = new int[XenoMeshFormat.TOTAL_STRIDE];
        this.baseIndex = 0;
        clear();
    }

    public void setBackingData(int[] data, int baseIndex) {
        load(data, baseIndex);
    }

    @Override
    public @NonNull QuadEmitter pos(int vertexIndex, float x, float y, float z) {
        super.pos(vertexIndex, x, y, z);
        return this;
    }

    @Override
    public @NonNull QuadEmitter color(int vertexIndex, int color) {
        super.color(vertexIndex, color);
        return this;
    }

    @Override
    public @NonNull QuadEmitter uv(int vertexIndex, float u, float v) {
        super.uv(vertexIndex, u, v);
        return this;
    }

    @Override
    public @NonNull QuadEmitter lightmap(int vertexIndex, int lightmap) {
        super.lightmap(vertexIndex, lightmap);
        return this;
    }

    @Override
    public @NonNull QuadEmitter normal(int vertexIndex, float x, float y, float z) {
        super.normal(vertexIndex, x, y, z);
        return this;
    }

    @Override
    public @NonNull QuadEmitter nominalFace(@Nullable Direction face) {
        super.nominalFace(face);
        return this;
    }

    @Override
    public @NonNull QuadEmitter cullFace(@Nullable Direction face) {
        super.cullFace(face);
        return this;
    }

    @Override
    public @NonNull QuadEmitter atlas(@NonNull QuadAtlas quadAtlas) {
        super.atlas(quadAtlas);
        return this;
    }

    @Override
    public @NonNull QuadEmitter chunkLayer(@NonNull ChunkSectionLayer layer) {
        super.chunkLayer(layer);
        return this;
    }

    @Override
    public @NonNull QuadEmitter itemRenderType(@NonNull RenderType renderType) {
        super.itemRenderType(renderType);
        return this;
    }

    @Override
    public @NonNull QuadEmitter itemGlintRenderType(@NonNull RenderType renderType) {
        super.itemGlintRenderType(renderType);
        return this;
    }

    @Override
    public @NonNull QuadEmitter itemGlintSpecialRenderType(@NonNull RenderType renderType) {
        super.itemGlintSpecialRenderType(renderType);
        return this;
    }

    @Override
    public @NonNull QuadEmitter emissive(boolean emissive) {
        super.emissive(emissive);
        return this;
    }

    @Override
    public @NonNull QuadEmitter shadeDirectionOverride(@Nullable Direction shadeDirection) {
        super.shadeDirectionOverride(shadeDirection);
        return this;
    }

    @Override
    public @NonNull QuadEmitter ambientOcclusion(@NonNull TriState ao) {
        super.ambientOcclusion(ao);
        return this;
    }

    @Override
    public @NonNull QuadEmitter foilType(ItemStackRenderState.@Nullable FoilType foilType) {
        super.foilType(foilType);
        return this;
    }

    @Override
    public @NonNull QuadEmitter shadeMode(@NonNull ShadeMode mode) {
        super.shadeMode(mode);
        return this;
    }

    @Override
    public @NonNull QuadEmitter animated(boolean animated) {
        super.animated(animated);
        return this;
    }

    @Override
    public @NonNull QuadEmitter tintIndex(int tintIndex) {
        super.tintIndex(tintIndex);
        return this;
    }

    @Override
    public @NonNull QuadEmitter tag(int tag) {
        super.tag(tag);
        return this;
    }

    @Override
    public @NonNull QuadEmitter copyFrom(@NonNull QuadView quad) {
        super.copyFrom(quad);
        return this;
    }

    @Override
    public @NonNull QuadEmitter fromBakedQuad(@NonNull BakedQuad quad) {
        super.fromBakedQuad(quad);
        return this;
    }

    @Override
    public @NonNull QuadEmitter clear() {
        super.clear();
        return this;
    }

    @Override
    public void pushTransform(@NonNull QuadTransform transform) {
        if (transform == null) {
            throw new NullPointerException("QuadTransform cannot be null");
        }
        this.transformStack.push(transform);
        if (this.transformStack.size() == 1) {
            this.activeTransform = transform;
        } else if (this.transformStack.size() == 2) {
            this.activeTransform = this.stackTransform;
        }
    }

    @Override
    public void popTransform() {
        this.transformStack.pop();
        if (this.transformStack.isEmpty()) {
            this.activeTransform = NO_TRANSFORM;
        } else if (this.transformStack.size() == 1) {
            this.activeTransform = this.transformStack.get(0);
        }
    }

    @Override
    public @NonNull QuadEmitter emit() {
        if (this.activeTransform.transform(this)) {
            emitDirectly();
        }
        clear();
        return this;
    }

    protected void emitDirectly() {
        if (this.outputConsumer != null) {
            this.outputConsumer.accept(this);
        }
    }
}
