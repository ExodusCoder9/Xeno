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

import com.xeno.fabric.client.render.frapi.quad.XenoQuadEmitter;
import com.xeno.fabric.client.render.frapi.quad.XenoQuadView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public abstract class XenoMeshView implements MeshView {
    private static final ThreadLocal<ObjectArrayList<XenoQuadView>> CURSOR_POOLS =
        ThreadLocal.withInitial(ObjectArrayList::new);

    protected int[] data;
    protected int limit;

    public int[] data() {
        return this.data;
    }

    @Override
    public int size() {
        return this.limit / XenoMeshFormat.TOTAL_STRIDE;
    }

    @Override
    public void forEach(@NonNull Consumer<? super QuadView> action) {
        ObjectArrayList<XenoQuadView> pool = CURSOR_POOLS.get();
        XenoQuadView quad = pool.isEmpty() ? new XenoQuadView() : pool.pop();
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
    public void outputTo(@NonNull QuadEmitter emitter) {
        int end = this.limit;

        if (emitter instanceof XenoQuadEmitter xenoEmitter) {
            int[] destination = xenoEmitter.data();
            int baseIndex = xenoEmitter.baseIndex();
            for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
                System.arraycopy(this.data, i, destination, baseIndex, XenoMeshFormat.TOTAL_STRIDE);
                xenoEmitter.emit();
                destination = xenoEmitter.data();
                baseIndex = xenoEmitter.baseIndex();
            }
            return;
        }

        ObjectArrayList<XenoQuadView> pool = CURSOR_POOLS.get();
        XenoQuadView quad = pool.isEmpty() ? new XenoQuadView() : pool.pop();
        try {
            for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
                quad.load(this.data, i);
                emitter.copyFrom(quad);
                emitter.emit();
            }
        } finally {
            quad.unload();
            pool.push(quad);
        }
    }
}
