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

import com.xeno.client.common.render.texture.XenoCompiledSectionMeshExtension;
import com.xeno.client.common.render.texture.XenoSectionResultsExtension;
import com.xeno.client.common.render.texture.XenoSpriteTracker;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public abstract class XenoCompiledSectionMeshMixin implements XenoCompiledSectionMeshExtension {
    @Unique
    private TextureAtlasSprite @Nullable [] xeno$animatedSprites;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xeno$copyAnimatedSprites(TranslucencyPointOfView translucencyPointOfView, SectionCompiler.Results results, long compileTaskStartTimeNs, CallbackInfo ci) {
        this.xeno$animatedSprites = ((XenoSectionResultsExtension) (Object) results).xeno$getAnimatedSprites();
    }

    @Override
    public void xeno$markAnimatedSpritesActive() {
        TextureAtlasSprite[] sprites = this.xeno$animatedSprites;
        if (sprites != null) {
            for (int i = 0; i < sprites.length; i++) {
                XenoSpriteTracker.markActive(sprites[i]);
            }
        }
    }
}
