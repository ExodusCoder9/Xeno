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

import com.xeno.client.common.render.chunk.XenoWorldRenderManager;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class XenoLevelExtractorMixin {
	@Inject(method = "setLevel", at = @At("TAIL"))
	private void xeno$onLevelSet(net.minecraft.client.multiplayer.ClientLevel level, CallbackInfo ci) {
		XenoWorldRenderManager.INSTANCE.onLevelSet(level);
	}

	@Inject(method = "allChanged", at = @At("TAIL"))
	private void xeno$onAllChanged(CallbackInfo ci) {
		XenoWorldRenderManager.INSTANCE.onRenderPipelineReset();
	}

	@Inject(
		method = "setSectionDirty(IIIZ)V",
		at = @At("TAIL")
	)
	private void xeno$onSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci) {
		XenoWorldRenderManager.INSTANCE.onSectionDirty(sectionX, sectionY, sectionZ, playerChanged);
	}

	@Inject(
		method = "extract",
		at = @At("TAIL")
	)
	private void xeno$onExtractEnd(net.minecraft.client.DeltaTracker deltaTracker, net.minecraft.client.Camera camera, float deltaPartialTick, CallbackInfo ci) {
		XenoWorldRenderManager.INSTANCE.endExtractFrame();
	}
}
