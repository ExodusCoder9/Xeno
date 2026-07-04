package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelWeatherRender(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}
}
