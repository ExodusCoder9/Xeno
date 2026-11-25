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
import com.mojang.renderpearl.api.commands.RenderPass;
import com.xeno.client.common.render.texture.XenoAnimationStateExtension;
import com.xeno.client.common.render.texture.XenoSpriteContentsExtension;
import com.xeno.client.common.render.texture.XenoSpriteTracker;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.AnimationState.class)
public abstract class XenoAnimationStateMixin implements XenoAnimationStateExtension {
    @Unique
    private SpriteContents xeno$owner;

    @Unique
    private boolean xeno$activeThisTick = true;

    @Override
    public void xeno$setOwner(SpriteContents owner) {
        this.xeno$owner = owner;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void xeno$skipInactiveTick(CallbackInfo ci) {
        if (!XenoSpriteTracker.isAnimationCullingEnabled() || this.xeno$owner == null) {
            this.xeno$activeThisTick = true;
            return;
        }
        XenoSpriteContentsExtension ext = (XenoSpriteContentsExtension) this.xeno$owner;
        boolean active = ext.xeno$isActive();
        ext.xeno$setActive(false);
        this.xeno$activeThisTick = active;
        if (!active) {
            ci.cancel();
        }
    }

    @Inject(method = "drawToAtlas", at = @At("HEAD"), cancellable = true)
    private void xeno$skipInactiveDraw(RenderPass renderPass, GpuBufferSlice ubo, CallbackInfo ci) {
        if (XenoSpriteTracker.isAnimationCullingEnabled() && this.xeno$owner != null && !this.xeno$activeThisTick) {
            ci.cancel();
        }
    }
}
