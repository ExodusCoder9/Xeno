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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
    }

    private void ensureCapacity(int stride) {
        if (this.limit + stride > this.data.length) {
            int newLength = Math.max(this.data.length * 2, this.limit + stride);
            this.data = Arrays.copyOf(this.data, newLength);
            this.emitter.updateBackingArray(this.data);
        }
    }

    @Override
    public @NonNull QuadEmitter emitter() {
        ensureCapacity(XenoMeshFormat.TOTAL_STRIDE);
        this.emitter.setBackingData(this.data, this.limit);
        return this.emitter;
    }

    private static final ThreadLocal<ObjectArrayList<XenoMutableQuadView>> MUTABLE_CURSOR_POOLS =
        ThreadLocal.withInitial(ObjectArrayList::new);

    @Override
    public void forEachMutable(@NonNull Consumer<? super MutableQuadView> action) {
        ObjectArrayList<XenoMutableQuadView> pool = MUTABLE_CURSOR_POOLS.get();
        XenoMutableQuadView quad = pool.isEmpty() ? new XenoMutableQuadView() : pool.pop();
        try {
            int end = this.limit;
            for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
                quad.load(this.data, i);
                action.accept(quad);
            }
        } finally {
            quad.unload();
            pool.push(quad);
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
        ensureCapacity(XenoMeshFormat.TOTAL_STRIDE);
        this.emitter.setBackingData(this.data, 0);
    }

    private final class MeshEmitter extends XenoQuadEmitter {
        MeshEmitter() {
            super(null);
        }

        void updateBackingArray(int[] data) {
            this.data = data;
        }

        @Override
        protected void emitDirectly() {
            computeGeometry();
            XenoMutableMesh.this.limit += XenoMeshFormat.TOTAL_STRIDE;
            XenoMutableMesh.this.ensureCapacity(XenoMeshFormat.TOTAL_STRIDE);
            this.data = XenoMutableMesh.this.data;
            this.baseIndex = XenoMutableMesh.this.limit;
            clear();
        }
    }
}
