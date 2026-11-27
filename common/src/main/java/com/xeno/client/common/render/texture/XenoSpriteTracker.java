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

package com.xeno.client.common.render.texture;

import com.xeno.client.common.config.XenoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class XenoSpriteTracker {
    private XenoSpriteTracker() {
    }

    public static boolean isAnimationCullingEnabled() {
        return XenoConfig.isAnimationCullingEnabled();
    }

    public static void setAnimationCullingEnabled(boolean enabled) {
        XenoConfig.setAnimationCullingEnabled(enabled);
    }

    public static void markActive(@Nullable TextureAtlasSprite sprite) {
        if (sprite != null) {
            ((XenoSpriteContentsExtension) sprite.contents()).xeno$setActive(true);
        }
    }

    public static boolean hasAnimation(@Nullable TextureAtlasSprite sprite) {
        return sprite != null && ((XenoSpriteContentsExtension) sprite.contents()).xeno$hasAnimation();
    }

    public static XenoSpriteFinder forAtlas(Identifier atlasId) {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(atlasId);
        return ((XenoTextureAtlasExtension) atlas).xeno$getSpriteFinder();
    }
}
