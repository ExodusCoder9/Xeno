package com.xeno.client;

import com.xeno.Initializer;
import net.fabricmc.api.ClientModInitializer;

public class XenoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Initializer.init();
	}
}