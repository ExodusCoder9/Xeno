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

import com.xeno.client.common.render.block.XenoLightDataCache;
import com.xeno.client.common.render.block.XenoQuadGeometry;
import com.xeno.client.common.render.block.XenoQuadLightResult;
import com.xeno.client.common.render.block.XenoQuadLighter;
import com.xeno.fabric.client.render.frapi.quad.XenoMutableQuadView;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.fabricmc.fabric.api.client.renderer.v1.render.ExtraLightCoordsUtil;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockTintsFactory;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
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

public final class XenoAltModelBlockRenderer implements AltModelBlockRenderer, QuadTransform {
    private static final ThreadLocal<XenoQuadLighter> LIGHTER =
        ThreadLocal.withInitial(() -> new XenoQuadLighter(XenoLightDataCache.get()));

    private final boolean ambientOcclusion;
    private final boolean cull;
    private final BlockColors blockColors;

    private final Predicate<@Nullable Direction> cullPredicate = this::shouldCullFace;
    private final RandomSource random = RandomSource.createThreadLocalInstance(0L);
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
    private final Vector3f offset = new Vector3f();
    private final MutableQuadViewGeometry quadGeometry = new MutableQuadViewGeometry();

    private long currentSection = Long.MIN_VALUE;

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
            @NonNull QuadEmitter output,
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

        int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        long section = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (section != this.currentSection) {
            this.currentSection = section;
            XenoLightDataCache.get().bindSection(sectionX, sectionY, sectionZ);
        }
        LIGHTER.get().resetForBlock(pos);

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
        boolean vanillaShade = quad.shadeMode() == ShadeMode.VANILLA;

        shadeQuad(quad, useAo, isEmissive, vanillaShade);
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

    private void shadeQuad(MutableQuadView quad, boolean ao, boolean emissive, boolean vanillaShade) {
        if (emissive) {
            quad.lightmap(0, LightCoordsUtil.FULL_BRIGHT);
            quad.lightmap(1, LightCoordsUtil.FULL_BRIGHT);
            quad.lightmap(2, LightCoordsUtil.FULL_BRIGHT);
            quad.lightmap(3, LightCoordsUtil.FULL_BRIGHT);
            return;
        }

        XenoQuadLightResult result = ao
            ? LIGHTER.get().computeAmbientOcclusion(
                this.level, this.blockState, this.pos, this.quadGeometry.bind(quad), !vanillaShade
            )
            : LIGHTER.get().computeFlat(
                this.level, this.blockState, this.pos, this.quadGeometry.bind(quad), quad.cullFace(), !vanillaShade
            );

        for (int i = 0; i < 4; i++) {
            applyQuadShade(quad, i, result.shades[i]);
            quad.lightmap(i, ExtraLightCoordsUtil.smoothMax(quad.lightmap(i), result.lightCoords[i]));
        }

        applyQuadColorScale(quad, result.directionalBrightness);
    }

    private static void applyQuadShade(MutableQuadView quad, int vertexIndex, float shade) {
        if (quad instanceof XenoMutableQuadView xenoQuad) {
            xenoQuad.applyShade(vertexIndex, shade);
        } else {
            quad.color(vertexIndex, ARGB.scaleRGB(quad.color(vertexIndex), shade));
        }
    }

    private static void applyQuadColorScale(MutableQuadView quad, float factor) {
        if (quad instanceof XenoMutableQuadView xenoQuad) {
            xenoQuad.scaleColor(factor);
        } else {
            for (int i = 0; i < 4; i++) {
                quad.color(i, ARGB.scaleRGB(quad.color(i), factor));
            }
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

    private static final class MutableQuadViewGeometry implements XenoQuadGeometry {
        private MutableQuadView quad;

        MutableQuadViewGeometry bind(MutableQuadView quad) {
            this.quad = quad;
            return this;
        }

        @Override
        public Direction direction() {
            return this.quad.lightFace();
        }

        @Override
        public @Nullable Direction shadeDirectionOverride() {
            return this.quad.shadeDirectionOverride();
        }

        @Override
        public float x(int vertexIndex) {
            return this.quad.x(vertexIndex);
        }

        @Override
        public float y(int vertexIndex) {
            return this.quad.y(vertexIndex);
        }

        @Override
        public float z(int vertexIndex) {
            return this.quad.z(vertexIndex);
        }
    }
}
