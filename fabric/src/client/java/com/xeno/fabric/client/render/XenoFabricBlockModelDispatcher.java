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

package com.xeno.fabric.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.client.common.render.block.XenoBlockModelDispatcher;
import com.xeno.client.common.render.block.XenoModelRenderer;
import com.xeno.client.common.render.chunk.XenoSectionCompiler;
import com.xeno.fabric.client.render.frapi.XenoRegistrar;
import com.xeno.fabric.client.render.frapi.XenoRenderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class XenoFabricBlockModelDispatcher implements XenoBlockModelDispatcher {
    private final ThreadLocal<ThreadLocalFrapiContext> contexts = ThreadLocal.withInitial(() -> {
        try {
            BlockColors blockColors = Minecraft.getInstance().getBlockColors();
            boolean ao = Minecraft.getInstance().options.ambientOcclusion().get();
            return new ThreadLocalFrapiContext(blockColors, ao);
        } catch (Throwable t) {
            return null;
        }
    });

    public static void register() {
        if (FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1")) {
            try {
                XenoRegistrar.register();
            } catch (Throwable ignored) {
            }
            try {
                XenoSectionCompiler.MODEL_DISPATCHER = new XenoFabricBlockModelDispatcher();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void tesselate(
        BlockQuadOutput output,
        float x, float y, float z,
        RenderSectionRegion region,
        BlockPos pos,
        BlockState blockState,
        BlockStateModel model,
        long seed,
        XenoModelRenderer blockRenderer,
        Function<ChunkSectionLayer, VertexConsumer> sinkProvider
    ) {
        ThreadLocalFrapiContext context = this.contexts.get();
        if (context != null && isCustomFrapiModel(model, context.random, context.scratchParts)) {
            context.currentSinkProvider = sinkProvider;
            try {
                context.altRenderer.tesselateBlock(context.quadEmitter, x, y, z, region, pos, blockState, model, seed);
            } finally {
                context.currentSinkProvider = null;
            }
        } else {
            blockRenderer.emit(output, x, y, z, region, pos, blockState, model, seed);
        }
    }

    private static boolean isCustomFrapiModel(BlockStateModel model, RandomSource random, List<BlockStateModelPart> scratchParts) {
        Class<?> modelClass = model.getClass();
        if (modelClass != SingleVariant.class && modelClass != WeightedVariants.class && modelClass != MultiPartModel.class) {
            return true;
        }
        scratchParts.clear();
        model.collectParts(random, scratchParts);
        for (int i = 0; i < scratchParts.size(); i++) {
            if (scratchParts.get(i).getClass() != SimpleModelWrapper.class) {
                scratchParts.clear();
                return true;
            }
        }
        scratchParts.clear();
        return false;
    }

    private static final class ThreadLocalFrapiContext {
        final AltModelBlockRenderer altRenderer;
        final QuadEmitter quadEmitter;
        final RandomSource random = RandomSource.createThreadLocalInstance(0L);
        final List<BlockStateModelPart> scratchParts = new ArrayList<>();
        Function<ChunkSectionLayer, VertexConsumer> currentSinkProvider;

        ThreadLocalFrapiContext(BlockColors blockColors, boolean ao) {
            this.altRenderer = XenoRenderer.INSTANCE.altModelBlockRenderer(ao, true, blockColors);
            this.quadEmitter = XenoRenderer.INSTANCE.quadEmitter(this::handleQuad);
        }

        private void handleQuad(MutableQuadView quad) {
            if (this.currentSinkProvider != null) {
                ChunkSectionLayer layer = quad.chunkLayer();
                VertexConsumer sink = this.currentSinkProvider.apply(layer);
                if (sink != null) {
                    quad.buffer(OverlayTexture.NO_OVERLAY, sink);
                }
            }
        }
    }
}
