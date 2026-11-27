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

package com.xeno.client.mixin;

import com.xeno.client.common.render.texture.XenoSectionResultsExtension;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionCompiler.Results.class)
public abstract class XenoSectionResultsMixin implements XenoSectionResultsExtension {
    @Unique
    private TextureAtlasSprite @Nullable [] xeno$animatedSprites;

    @Unique
    private long @Nullable [] xeno$directionalMasks;

    @Unique
    private boolean xeno$hasTightBounds;

    @Unique
    private float xeno$minX, xeno$minY, xeno$minZ;

    @Unique
    private float xeno$maxX, xeno$maxY, xeno$maxZ;

    @Override
    public TextureAtlasSprite @Nullable [] xeno$getAnimatedSprites() {
        return this.xeno$animatedSprites;
    }

    @Override
    public void xeno$setAnimatedSprites(TextureAtlasSprite @Nullable [] sprites) {
        this.xeno$animatedSprites = sprites;
    }

    @Override
    public long @Nullable [] xeno$getDirectionalMasks() {
        return this.xeno$directionalMasks;
    }

    @Override
    public void xeno$setDirectionalMasks(long @Nullable [] masks) {
        this.xeno$directionalMasks = masks;
    }

    @Override
    public boolean xeno$hasTightBounds() {
        return this.xeno$hasTightBounds;
    }

    @Override
    public void xeno$setTightBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.xeno$hasTightBounds = true;
        this.xeno$minX = minX;
        this.xeno$minY = minY;
        this.xeno$minZ = minZ;
        this.xeno$maxX = maxX;
        this.xeno$maxY = maxY;
        this.xeno$maxZ = maxZ;
    }

    @Override
    public float xeno$minX() {
        return this.xeno$minX;
    }

    @Override
    public float xeno$minY() {
        return this.xeno$minY;
    }

    @Override
    public float xeno$minZ() {
        return this.xeno$minZ;
    }

    @Override
    public float xeno$maxX() {
        return this.xeno$maxX;
    }

    @Override
    public float xeno$maxY() {
        return this.xeno$maxY;
    }

    @Override
    public float xeno$maxZ() {
        return this.xeno$maxZ;
    }
}
