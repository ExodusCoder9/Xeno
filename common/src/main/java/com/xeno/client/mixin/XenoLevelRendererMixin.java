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

import com.xeno.client.common.render.chunk.XenoRegionCompiler;
import com.xeno.client.common.render.chunk.XenoTerrainDrawer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class XenoLevelRendererMixin {
	@Inject(
		method = "render",
		at = @At("TAIL")
	)
	private void xeno$flushRegionUploads(CallbackInfo ci) {
		XenoRegionCompiler.INSTANCE.flushUploads();
	}

	/**
	 * @author ExodusCoder9
	 * @reason Draw lists are built from Xeno owned region meshes instead of vanilla's one called visibleSections.
	 */
	@Redirect(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareChunkRenders(Lorg/joml/Matrix4fc;)Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;"
		)
	)
	private ChunkSectionsToRender xeno$takeOverDrawListBuilding(LevelRenderer instance, Matrix4fc modelViewMatrix) {
		return XenoTerrainDrawer.INSTANCE.prepareChunkRenders(instance, modelViewMatrix);
	}
}
