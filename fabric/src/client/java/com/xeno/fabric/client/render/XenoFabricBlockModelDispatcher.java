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

import com.xeno.client.common.render.block.XenoBlockModelDispatcher;
import com.xeno.client.common.render.block.XenoModelRenderer;
import com.xeno.client.common.render.chunk.XenoSectionCompiler;
import com.xeno.client.common.render.chunk.XenoSectionLayerBuffer;
import com.xeno.fabric.client.render.frapi.XenoRegistrar;
import com.xeno.fabric.client.render.frapi.XenoRenderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class XenoFabricBlockModelDispatcher implements XenoBlockModelDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("XenoFrapi");

    private final ThreadLocal<ThreadLocalFrapiContext> contexts = new ThreadLocal<>();

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1")) {
            return;
        }

        try {
            XenoRegistrar.register();
        } catch (UnsupportedOperationException e) {
            LOGGER.error(
                "Another Fabric Renderer API provider is already registered, so Xeno's FRAPI support is disabled. "
                    + "Remove Sodium or Iris' FRAPI module, or Xeno, to avoid this conflict.",
                e
            );
            return;
        } catch (Throwable t) {
            LOGGER.error("Failed to register Xeno's Fabric Renderer API provider", t);
            return;
        }

        try {
            XenoSectionCompiler.MODEL_DISPATCHER = new XenoFabricBlockModelDispatcher();
        } catch (Throwable t) {
            LOGGER.error("Failed to install Xeno's FRAPI block model dispatcher", t);
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
        Function<ChunkSectionLayer, XenoSectionLayerBuffer> sinkProvider
    ) {
        ThreadLocalFrapiContext context = this.contexts.get();
        BlockColors blockColors = blockRenderer.blockColors();
        boolean ao = blockRenderer.ambientOcclusion();

        if (context == null || context.ambientOcclusion != ao || context.blockColors != blockColors) {
            context = new ThreadLocalFrapiContext(blockColors, ao);
            this.contexts.set(context);
        }
        if (!isVanillaModel(model)) {
            emitWithFrapi(context, x, y, z, region, pos, blockState, model, seed, sinkProvider);
            return;
        }
        List<BlockStateModelPart> parts = context.scratchParts;
        parts.clear();
        try {
            context.random.setSeed(seed);
            model.collectParts(context.random, parts);

            if (requiresFrapiPath(parts)) {
                emitWithFrapi(context, x, y, z, region, pos, blockState, model, seed, sinkProvider);
            } else {
                blockRenderer.emitCollected(output, x, y, z, region, pos, blockState, parts);
            }
        } finally {
            parts.clear();
        }
    }

    private static void emitWithFrapi(
        ThreadLocalFrapiContext context,
        float x, float y, float z,
        RenderSectionRegion region,
        BlockPos pos,
        BlockState blockState,
        BlockStateModel model,
        long seed,
        Function<ChunkSectionLayer, XenoSectionLayerBuffer> sinkProvider
    ) {
        context.currentSinkProvider = sinkProvider;
        try {
            context.altRenderer.tesselateBlock(context.quadEmitter, x, y, z, region, pos, blockState, model, seed);
        } finally {
            context.currentSinkProvider = null;
        }
    }

    private static boolean isVanillaModel(BlockStateModel model) {
        return model instanceof SingleVariant
            || model instanceof WeightedVariants
            || model instanceof MultiPartModel;
    }

    private static boolean requiresFrapiPath(List<BlockStateModelPart> parts) {
        for (int i = 0; i < parts.size(); i++) {
            BlockStateModelPart part = parts.get(i);
            if (!(part instanceof SimpleModelWrapper wrapper)) {
                return true;
            }
            if (wrapper.quads() instanceof MeshQuadCollection) {
                return true;
            }
        }
        return false;
    }

    private static final class ThreadLocalFrapiContext {
        final BlockColors blockColors;
        final boolean ambientOcclusion;
        final AltModelBlockRenderer altRenderer;
        final QuadEmitter quadEmitter;
        final RandomSource random = RandomSource.createThreadLocalInstance(0L);
        final List<BlockStateModelPart> scratchParts = new ArrayList<>();
        Function<ChunkSectionLayer, XenoSectionLayerBuffer> currentSinkProvider;

        ThreadLocalFrapiContext(BlockColors blockColors, boolean ao) {
            this.blockColors = blockColors;
            this.ambientOcclusion = ao;
            this.altRenderer = XenoRenderer.INSTANCE.altModelBlockRenderer(ao, true, blockColors);
            this.quadEmitter = XenoRenderer.INSTANCE.quadEmitter(this::handleQuad);
        }

        private void handleQuad(MutableQuadView quad) {
            if (this.currentSinkProvider == null) {
                return;
            }

            XenoSectionLayerBuffer sink = this.currentSinkProvider.apply(quad.chunkLayer());
            if (sink == null) {
                return;
            }

            sink.writeFrapiQuad(
                quad.x(0), quad.y(0), quad.z(0), quad.u(0), quad.v(0),
                quad.x(1), quad.y(1), quad.z(1), quad.u(1), quad.v(1),
                quad.x(2), quad.y(2), quad.z(2), quad.u(2), quad.v(2),
                quad.x(3), quad.y(3), quad.z(3), quad.u(3), quad.v(3),
                quad.color(0), quad.color(1), quad.color(2), quad.color(3),
                quad.lightmap(0), quad.lightmap(1), quad.lightmap(2), quad.lightmap(3)
            );
        }
    }
}
