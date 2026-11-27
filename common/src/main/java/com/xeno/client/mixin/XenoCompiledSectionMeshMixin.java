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
 * along with this program.  If not, see <https://gnu.org/licenses/>.
 */

package com.xeno.client.mixin;

import com.xeno.client.common.render.texture.XenoCompiledSectionMeshExtension;
import com.xeno.client.common.render.texture.XenoSectionResultsExtension;
import com.xeno.client.common.render.texture.XenoSpriteTracker;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public abstract class XenoCompiledSectionMeshMixin implements XenoCompiledSectionMeshExtension {
    @Unique
    private static final Direction[] XENO_DIRECTIONS = Direction.values();

    @Shadow
    @Final
    private VisibilitySet visibilitySet;

    @Unique
    private TextureAtlasSprite @Nullable [] xeno$animatedSprites;

    @Unique
    private long xeno$visibilityMask;

    @Unique
    private long @Nullable [] xeno$directionalMasks;

    @Unique
    private boolean xeno$hasTightBounds;

    @Unique
    private float xeno$minX, xeno$minY, xeno$minZ;

    @Unique
    private float xeno$maxX, xeno$maxY, xeno$maxZ;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xeno$copyAnimatedSprites(TranslucencyPointOfView translucencyPointOfView, SectionCompiler.Results results, long compileTaskStartTimeNs, CallbackInfo ci) {
        if ((Object) results instanceof XenoSectionResultsExtension ext) {
            this.xeno$animatedSprites = ext.xeno$getAnimatedSprites();
            this.xeno$directionalMasks = ext.xeno$getDirectionalMasks();

            if (ext.xeno$hasTightBounds()) {
                this.xeno$hasTightBounds = true;
                this.xeno$minX = ext.xeno$minX();
                this.xeno$minY = ext.xeno$minY();
                this.xeno$minZ = ext.xeno$minZ();
                this.xeno$maxX = ext.xeno$maxX();
                this.xeno$maxY = ext.xeno$maxY();
                this.xeno$maxZ = ext.xeno$maxZ();
            }
        }

        if (this.xeno$directionalMasks != null) {
            return;
        }
        long mask = 0L;
        VisibilitySet vis = this.visibilitySet;
        if (vis != null) {
            for (int exit = 0; exit < 6; exit++) {
                Direction exitDir = XENO_DIRECTIONS[exit];
                int rowShift = exit * 6;
                for (int enter = 0; enter < 6; enter++) {
                    if (vis.visibilityBetween(XENO_DIRECTIONS[enter].getOpposite(), exitDir)) {
                        mask |= 1L << (rowShift + enter);
                    }
                }
            }
        }
        this.xeno$visibilityMask = mask;
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

    @Override
    public long xeno$getVisibilityMask() {
        return this.xeno$visibilityMask;
    }

    @Override
    public long @Nullable [] xeno$getDirectionalMasks() {
        return this.xeno$directionalMasks;
    }

    @Override
    public boolean xeno$hasTightBounds() {
        return this.xeno$hasTightBounds;
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
