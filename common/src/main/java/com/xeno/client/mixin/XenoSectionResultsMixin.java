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

    @Override
    public TextureAtlasSprite @Nullable [] xeno$getAnimatedSprites() {
        return this.xeno$animatedSprites;
    }

    @Override
    public void xeno$setAnimatedSprites(TextureAtlasSprite @Nullable [] sprites) {
        this.xeno$animatedSprites = sprites;
    }
}
