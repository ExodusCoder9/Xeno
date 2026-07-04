package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {
	@Inject(
		method = "extractRenderState",
		at = @At("TAIL")
	)
	private void xeno_addDebugInfo(net.minecraft.client.gui.GuiGraphicsExtractor graphics, CallbackInfo ci) {
		// Placeholder for Xeno debug overlay integration
	}
}
