package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {

	@Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelRenderSkyDisc(int skyColor, CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}

	@Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelRenderDarkDisc(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelRenderSunMoonAndStars(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}

	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelRenderSunriseAndSunset(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}

	@Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelRenderEndSky(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}
}
