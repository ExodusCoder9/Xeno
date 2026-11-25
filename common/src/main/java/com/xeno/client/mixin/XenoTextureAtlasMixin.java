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

import com.xeno.client.common.render.texture.XenoSpriteFinder;
import com.xeno.client.common.render.texture.XenoSpriteTracker;
import com.xeno.client.common.render.texture.XenoTextureAtlasExtension;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(TextureAtlas.class)
public abstract class XenoTextureAtlasMixin implements XenoTextureAtlasExtension {
    @Shadow
    private List<TextureAtlasSprite> sprites;

    @Shadow
    private TextureAtlasSprite missingSprite;

    @Unique
    private volatile XenoSpriteFinder xeno$spriteFinder;

    @Override
    public XenoSpriteFinder xeno$getSpriteFinder() {
        XenoSpriteFinder finder = this.xeno$spriteFinder;
        if (finder == null) {
            finder = new XenoSpriteFinder(this.sprites, this.missingSprite);
            this.xeno$spriteFinder = finder;
        }
        return finder;
    }

    @Inject(method = "upload", at = @At("RETURN"))
    private void xeno$onUpload(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        this.xeno$spriteFinder = null;
    }

    @Inject(method = "getSprite", at = @At("RETURN"))
    private void xeno$markSpriteActive(CallbackInfoReturnable<TextureAtlasSprite> cir) {
        XenoSpriteTracker.markActive(cir.getReturnValue());
    }
}
