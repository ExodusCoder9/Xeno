package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelBlockEntitySubmit(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}
}
