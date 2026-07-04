package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

	@Redirect(
		method = "extract",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z")
	)
	private boolean xeno_consumeFrustumUpdate(SectionOcclusionGraph graph) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			return false;
		}
		return graph.consumeFrustumUpdate();
	}

	@Inject(
		method = "extract",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;applyFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)V"),
		cancellable = true
	)
	private void xeno_cancelApplyFrustum(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			ci.cancel();
		}
	}
}
