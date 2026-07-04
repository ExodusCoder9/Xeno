package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void xeno_cancelBlockEntitySubmit(
		BlockEntityRenderState state,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		CameraRenderState camera,
		CallbackInfo ci
	) {
		if (XenoConfig.INSTANCE.enableXenoEntities) {
			ci.cancel();
		}
	}
}
