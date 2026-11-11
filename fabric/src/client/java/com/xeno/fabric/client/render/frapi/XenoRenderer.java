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

package com.xeno.fabric.client.render.frapi;

import com.xeno.fabric.client.render.frapi.mesh.XenoMutableMesh;
import com.xeno.fabric.client.render.frapi.quad.XenoQuadEmitter;
import com.xeno.fabric.client.render.frapi.render.XenoAltModelBlockRenderer;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.minecraft.client.color.block.BlockColors;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public final class XenoRenderer implements Renderer {
    public static final XenoRenderer INSTANCE = new XenoRenderer();

    private XenoRenderer() {}

    @Override
    public @NonNull QuadEmitter quadEmitter(@NonNull Consumer<? super MutableQuadView> consumer) {
        return new XenoQuadEmitter(consumer);
    }

    @Override
    public @NonNull MutableMesh mutableMesh() {
        return new XenoMutableMesh();
    }

    @Override
    public @NonNull AltModelBlockRenderer altModelBlockRenderer(boolean ambientOcclusion, boolean cull, @NonNull BlockColors blockColors) {
        return new XenoAltModelBlockRenderer(ambientOcclusion, cull, blockColors);
    }
}
