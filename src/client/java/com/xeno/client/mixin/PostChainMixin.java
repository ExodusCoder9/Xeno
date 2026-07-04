package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostChain.class)
public class PostChainMixin {

	@Inject(method = "addToFrame", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelPostChain(FrameGraphBuilder frame, int screenWidth, int screenHeight, PostChain.TargetBundle providedTargets, CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoEntities) {
			ci.cancel();
		}
	}
}
