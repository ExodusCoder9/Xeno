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

package com.xeno.fabric.client.render.frapi.mesh;

import com.xeno.fabric.client.render.frapi.quad.XenoMutableQuadView;
import com.xeno.fabric.client.render.frapi.quad.XenoQuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.function.Consumer;

public final class XenoMutableMesh extends XenoMeshView implements MutableMesh {
    private final MeshEmitter emitter;

    public XenoMutableMesh() {
        this.data = new int[8 * XenoMeshFormat.TOTAL_STRIDE];
        this.limit = 0;
        this.emitter = new MeshEmitter();
        this.emitter.setBackingData(this.data, 0);
        this.emitter.clear();
    }

    private void ensureCapacity() {
        if (this.limit + XenoMeshFormat.TOTAL_STRIDE > this.data.length) {
            int newLength = Math.max(this.data.length * 2, this.limit + XenoMeshFormat.TOTAL_STRIDE);
            this.data = Arrays.copyOf(this.data, newLength);
            this.emitter.setBackingData(this.data, this.limit);
        }
    }

    @Override
    public @NonNull QuadEmitter emitter() {
        ensureCapacity();
        this.emitter.setBackingData(this.data, this.limit);
        this.emitter.clear();
        return this.emitter;
    }

    @Override
    public void forEachMutable(@NonNull Consumer<? super MutableQuadView> action) {
        XenoMutableQuadView quad = new XenoMutableQuadView();
        int end = this.limit;
        for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
            quad.load(this.data, i);
            action.accept(quad);
        }
    }

    @Override
    public @NonNull Mesh immutableCopy() {
        int[] copy = Arrays.copyOf(this.data, this.limit);
        return new XenoMesh(copy);
    }

    @Override
    public void clear() {
        this.limit = 0;
        this.emitter.setBackingData(this.data, 0);
        this.emitter.clear();
    }

    private final class MeshEmitter extends XenoQuadEmitter {
        MeshEmitter() {
            super(null);
        }

        @Override
        protected void emitDirectly() {
            faceNormal();
            XenoMutableMesh.this.limit += XenoMeshFormat.TOTAL_STRIDE;
            XenoMutableMesh.this.ensureCapacity();
            setBackingData(XenoMutableMesh.this.data, XenoMutableMesh.this.limit);
        }
    }
}
