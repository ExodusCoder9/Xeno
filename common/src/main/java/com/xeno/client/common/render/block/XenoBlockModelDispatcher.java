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

package com.xeno.client.common.render.block;

import com.xeno.client.common.render.chunk.XenoSectionLayerBuffer;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

@FunctionalInterface
public interface XenoBlockModelDispatcher {
    XenoBlockModelDispatcher DEFAULT = (output, x, y, z, region, pos, blockState, model, seed, blockRenderer, sinkProvider) ->
        blockRenderer.emit(output, x, y, z, region, pos, blockState, model, seed);

    void tesselate(
        BlockQuadOutput output,
        float x, float y, float z,
        RenderSectionRegion region,
        BlockPos pos,
        BlockState blockState,
        BlockStateModel model,
        long seed,
        XenoModelRenderer blockRenderer,
        Function<ChunkSectionLayer, XenoSectionLayerBuffer> sinkProvider
    );
}
