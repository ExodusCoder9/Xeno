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

package com.xeno.client.common.render.block;

import com.xeno.client.mixin.RenderSectionRegionAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class XenoTintRegion implements BlockAndTintGetter {
    private final XenoSectionTintCache tintCache = new XenoSectionTintCache();
    private RenderSectionRegion region;
    private ClientLevel level;

    public void bind(RenderSectionRegion region, SectionPos sectionPos, int biomeBlendRadius) {
        this.region = region;
        this.level = ((RenderSectionRegionAccessor) region).xeno$getLevel();
        this.tintCache.reset(sectionPos, biomeBlendRadius);
    }

    public void release() {
        this.region = null;
        this.level = null;
    }

    public int getBlockTint(@NonNull BlockPos pos, @NonNull ColorResolver resolver) {
        return this.tintCache.getBlockTint(this.level, pos, resolver);
    }

    @Override
    public @NonNull BlockState getBlockState(@NonNull BlockPos pos) {
        return this.region.getBlockState(pos);
    }

    @Override
    public @NonNull FluidState getFluidState(@NonNull BlockPos pos) {
        return this.region.getFluidState(pos);
    }

    @Override
    public @NonNull CardinalLighting cardinalLighting() {
        return this.region.cardinalLighting();
    }

    @Override
    public @NonNull LevelLightEngine getLightEngine() {
        return this.region.getLightEngine();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(@NonNull BlockPos pos) {
        return this.region.getBlockEntity(pos);
    }

    @Override
    public int getMinY() {
        return this.region.getMinY();
    }

    @Override
    public int getHeight() {
        return this.region.getHeight();
    }
}
