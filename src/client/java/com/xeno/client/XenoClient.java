package com.xeno.client;

import com.xeno.client.render.XenoWorldRenderer;
import net.fabricmc.api.ClientModInitializer;

public class XenoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		XenoWorldRenderer.initialize();
	}
}
