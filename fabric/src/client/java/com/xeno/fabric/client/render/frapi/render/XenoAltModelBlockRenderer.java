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

package com.xeno.fabric.client.render.frapi.render;

import com.xeno.fabric.client.render.frapi.quad.XenoMutableQuadView;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockTintsFactory;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class XenoAltModelBlockRenderer implements AltModelBlockRenderer, QuadTransform {
    private final boolean ambientOcclusion;
    private final boolean cull;
    private final BlockColors blockColors;

    private final Predicate<@Nullable Direction> cullPredicate = this::shouldCullFace;
    private final RandomSource random = RandomSource.createThreadLocalInstance(0L);
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
    private final Vector3f offset = new Vector3f();

    private int cacheValidMask;
    private int shouldCullFaceCache;

    private int tintCacheIndex = -1;
    private int tintCacheValue;
    private boolean tintSourcesInitialized;
    private final List<@Nullable BlockTintSource> tintSources = new ObjectArrayList<>();
    private final IntList computedTintValues = new IntArrayList();

    private BlockAndTintGetter level;
    private BlockPos pos;
    private BlockState blockState;
    private boolean defaultAo;

    public XenoAltModelBlockRenderer(boolean ambientOcclusion, boolean cull, BlockColors blockColors) {
        this.ambientOcclusion = ambientOcclusion;
        this.cull = cull;
        this.blockColors = blockColors;
    }

    @Override
    public void tesselateBlock(
            QuadEmitter output,
            float x, float y, float z,
            @NonNull BlockAndTintGetter level,
            @NonNull BlockPos pos,
            BlockState blockState,
            @NonNull BlockStateModel model,
            long seed
    ) {
        Vec3 blockOffset = blockState.getOffset(pos);
        this.offset.set((float) (x + blockOffset.x), (float) (y + blockOffset.y), (float) (z + blockOffset.z));
        this.level = level;
        this.pos = pos;
        this.blockState = blockState;
        this.defaultAo = this.ambientOcclusion && blockState.getLightEmission() == 0;

        this.cacheValidMask = 0;
        this.shouldCullFaceCache = 0;

        this.random.setSeed(seed);
        output.clear();
        output.pushTransform(this);

        try {
            ((FabricBlockStateModel) model).emitQuads(
                output, level, pos, blockState, this.random, this.cull ? this.cullPredicate : _ -> false
            );
        } finally {
            output.popTransform();
            this.level = null;
            resetTintCache();
        }
    }

    @Override
    public boolean transform(@NonNull MutableQuadView quad) {
        if (this.cull && shouldCullFace(quad.cullFace())) {
            return false;
        }

        boolean useAo = this.ambientOcclusion && quad.ambientOcclusion().orElse(this.defaultAo);
        boolean isEmissive = quad.emissive();

        shadeQuad((XenoMutableQuadView) quad, useAo, isEmissive);
        tintQuad(quad);
        quad.translate(this.offset.x, this.offset.y, this.offset.z);
        return true;
    }

    private boolean shouldCullFace(@Nullable Direction direction) {
        if (direction == null) return false;
        int mask = 1 << direction.ordinal();
        if ((this.cacheValidMask & mask) == 0) {
            this.cacheValidMask |= mask;
            this.scratchPos.setWithOffset(this.pos, direction);
            BlockState neighborState = this.level.getBlockState(this.scratchPos);
            if (!Block.shouldRenderFace(this.blockState, neighborState, direction)) {
                this.shouldCullFaceCache |= mask;
                return true;
            }
            return false;
        }
        return (this.shouldCullFaceCache & mask) != 0;
    }

    private void shadeQuad(XenoMutableQuadView quad, boolean ao, boolean emissive) {
        if (emissive) {
            quad.lightmap(0, LightCoordsUtil.FULL_BRIGHT);
            quad.lightmap(1, LightCoordsUtil.FULL_BRIGHT);
            quad.lightmap(2, LightCoordsUtil.FULL_BRIGHT);
            quad.lightmap(3, LightCoordsUtil.FULL_BRIGHT);
            return;
        }

        Direction lightFace = quad.lightFace();
        Direction shadeDir = quad.shadeDirectionOverride();
        Direction effectiveDir = shadeDir != null ? shadeDir : lightFace;

        float brightness = this.level.cardinalLighting().byFace(effectiveDir);
        if (ao) {
            this.scratchPos.setWithOffset(this.pos, lightFace);
            if (this.level.getBlockState(this.scratchPos).isSolidRender()) {
                brightness *= 0.85F;
            }
        }

        int lightCoords = LightCoordsUtil.getLightCoords(this.level, this.neighborPos.setWithOffset(this.pos, lightFace));

        for (int i = 0; i < 4; i++) {
            quad.color(i, ARGB.scaleRGB(quad.color(i), brightness));
            int existingLight = quad.lightmap(i);
            int blockLight = Math.max(LightCoordsUtil.block(existingLight), LightCoordsUtil.block(lightCoords));
            int skyLight = Math.max(LightCoordsUtil.sky(existingLight), LightCoordsUtil.sky(lightCoords));
            quad.lightmap(i, LightCoordsUtil.pack(blockLight, skyLight));
        }
    }

    private void tintQuad(MutableQuadView quad) {
        int tintIndex = quad.tintIndex();
        if (tintIndex != -1) {
            int tint = getTintColor(this.level, this.blockState, this.pos, tintIndex);
            quad.multiplyColor(tint);
        }
    }

    private void configureTintCache(BlockState blockState, BlockAndTintGetter level, BlockPos pos) {
        List<BlockTintSource> sources = this.blockColors.getTintSources(blockState);
        int count = sources.size();
        if (count > 0) {
            this.tintSources.addAll(sources);
            for (int i = 0; i < count; i++) {
                this.computedTintValues.add(-1);
            }
        } else {
            BlockTintsFactory factory = BlockColorRegistry.getFactory(blockState);
            if (factory != null) {
                factory.collect(blockState, level, pos, this.computedTintValues);
            }
            if (!this.computedTintValues.isEmpty()) {
                for (int i = 0; i < this.computedTintValues.size(); i++) {
                    this.tintSources.add(null);
                }
            }
        }
    }

    private int computeTintColor(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex) {
        if (!this.tintSourcesInitialized) {
            configureTintCache(state, level, pos);
            this.tintSourcesInitialized = true;
        }
        if (tintIndex >= this.tintSources.size()) {
            return -1;
        }
        BlockTintSource source = this.tintSources.set(tintIndex, null);
        if (source != null) {
            int val = source.colorInWorld(state, level, pos);
            this.computedTintValues.set(tintIndex, val);
            return val;
        } else {
            return this.computedTintValues.getInt(tintIndex);
        }
    }

    private int getTintColor(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex) {
        if (this.tintCacheIndex == tintIndex) {
            return this.tintCacheValue;
        }
        int color = computeTintColor(level, state, pos, tintIndex);
        this.tintCacheIndex = tintIndex;
        this.tintCacheValue = color;
        return color;
    }

    private void resetTintCache() {
        this.tintCacheIndex = -1;
        if (this.tintSourcesInitialized) {
            this.tintSources.clear();
            this.computedTintValues.clear();
            this.tintSourcesInitialized = false;
        }
    }
}
