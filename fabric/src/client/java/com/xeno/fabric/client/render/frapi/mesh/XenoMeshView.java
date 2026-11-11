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

import com.xeno.fabric.client.render.frapi.quad.XenoQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public abstract class XenoMeshView implements MeshView {
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
        XenoQuadView quad = new XenoQuadView();
        int end = this.limit;
        for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
            quad.load(this.data, i);
            action.accept(quad);
        }
    }

    @Override
    public void outputTo(@NonNull QuadEmitter emitter) {
        XenoQuadView quad = new XenoQuadView();
        int end = this.limit;
        for (int i = 0; i < end; i += XenoMeshFormat.TOTAL_STRIDE) {
            quad.load(this.data, i);
            emitter.copyFrom(quad);
            emitter.emit();
        }
    }
}
