package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public class ViewAreaMixin {
	@Inject(
		method = "repositionCamera",
		at = @At("HEAD"),
		cancellable = true
	)
	private void xeno_cancelRepositionCamera(SectionPos cameraSectionPos, CallbackInfoReturnable<Boolean> cir) {
		// Do not cancel repositionCamera so that vanilla ViewArea correctly repositions chunk sections.
	}
}
