package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelEntitySubmit(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoEntities) {
			ci.cancel();
		}
	}
}
