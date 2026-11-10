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
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.mixin;

import com.xeno.client.common.render.entity.XenoParallelBlockEntityExtractor;
import com.xeno.client.common.render.entity.XenoParallelEntityExtractor;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class XenoLevelExtractorMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelRenderer levelRenderer;

    @Shadow
    private ClientLevel level;

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"), cancellable = true)
    private void xeno$onExtractVisibleEntities(
            Camera camera,
            Frustum frustum,
            DeltaTracker deltaTracker,
            LevelRenderState output,
            CallbackInfo ci
    ) {
        if (XenoParallelEntityExtractor.extractEntities(
                this.minecraft,
                this.level,
                this.levelRenderer,
                camera,
                frustum,
                deltaTracker,
                output
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "extractVisibleBlockEntities", at = @At("HEAD"), cancellable = true)
    private void xeno$onExtractVisibleBlockEntities(
            Camera camera,
            float deltaPartialTick,
            LevelRenderState levelRenderState,
            CallbackInfo ci
    ) {
        if (XenoParallelBlockEntityExtractor.extractBlockEntities(
                this.minecraft,
                this.level,
                this.levelRenderer,
                camera,
                deltaPartialTick,
                levelRenderState
        )) {
            ci.cancel();
        }
    }
}
