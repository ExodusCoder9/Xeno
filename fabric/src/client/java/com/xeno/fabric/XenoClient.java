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

package com.xeno.fabric;

import com.xeno.fabric.client.render.XenoFabricBlockModelDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class XenoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		XenoFabricBlockModelDispatcher.register();
		if (FabricLoader.getInstance().isModLoaded("fabric-renderer-api-v1")) {
			FrapiFeatureRenderers.register();
		}
	}

	private static final class FrapiFeatureRenderers {
		static void register() {
			net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry.register(
				net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit.TYPE,
				com.xeno.fabric.client.render.frapi.render.XenoExtendedBlockModelRenderer::new
			);
			net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry.register(
				net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedItemSubmit.TYPE,
				com.xeno.fabric.client.render.frapi.render.XenoExtendedItemRenderer::new
			);
		}
	}
}
