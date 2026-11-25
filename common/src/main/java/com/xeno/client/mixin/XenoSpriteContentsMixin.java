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

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.xeno.client.common.render.texture.XenoAnimationStateExtension;
import com.xeno.client.common.render.texture.XenoSpriteContentsExtension;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteContents.class)
public abstract class XenoSpriteContentsMixin implements XenoSpriteContentsExtension {
    @Shadow
    public abstract boolean isAnimated();

    @Unique
    private boolean xeno$active;

    @Override
    public boolean xeno$hasAnimation() {
        return this.isAnimated();
    }

    @Override
    public boolean xeno$isActive() {
        return this.xeno$active;
    }

    @Override
    public void xeno$setActive(boolean active) {
        this.xeno$active = active;
    }

    @Inject(method = "createAnimationState", at = @At("RETURN"))
    private void xeno$attachOwner(GpuBufferSlice uboSlice, int spriteUboSize, CallbackInfoReturnable<SpriteContents.AnimationState> cir) {
        SpriteContents.AnimationState state = cir.getReturnValue();
        if (state != null) {
            ((XenoAnimationStateExtension) state).xeno$setOwner((SpriteContents) (Object) this);
        }
    }
}
