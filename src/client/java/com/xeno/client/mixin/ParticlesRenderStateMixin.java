package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticlesRenderState.class)
public class ParticlesRenderStateMixin {

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelParticlesSubmit(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoEntities) {
			ci.cancel();
		}
	}
}
