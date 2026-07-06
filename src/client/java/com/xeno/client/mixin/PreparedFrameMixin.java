package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoEntityStats;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public class PreparedFrameMixin {
    @Inject(method = "executeSolid", at = @At("HEAD"), cancellable = true)
    private void xeno_checkSolidEnabled(CallbackInfo ci) {
        if (!XenoEntityStats.get().featuresEnabled) {
            ci.cancel();
            return;
        }
        XenoEntityStats.get().recordPhaseExecuted();
    }

    @Inject(method = "executeTranslucent", at = @At("HEAD"), cancellable = true)
    private void xeno_checkTranslucentEnabled(CallbackInfo ci) {
        if (!XenoEntityStats.get().featuresEnabled) {
            ci.cancel();
            return;
        }
        XenoEntityStats.get().recordPhaseExecuted();
    }

    @Inject(method = "executeOutline", at = @At("HEAD"), cancellable = true)
    private void xeno_checkOutlineEnabled(CallbackInfo ci) {
        if (!XenoEntityStats.get().featuresEnabled) {
            ci.cancel();
            return;
        }
        XenoEntityStats.get().recordPhaseExecuted();
    }

    @Inject(method = "executeTranslucentAfterTerrain", at = @At("HEAD"), cancellable = true)
    private void xeno_checkAfterTerrainEnabled(CallbackInfo ci) {
        if (!XenoEntityStats.get().featuresEnabled) {
            ci.cancel();
            return;
        }
        XenoEntityStats.get().recordPhaseExecuted();
    }

    @Inject(method = "executeAlwaysOnTop", at = @At("HEAD"), cancellable = true)
    private void xeno_checkAlwaysOnTopEnabled(CallbackInfo ci) {
        if (!XenoEntityStats.get().featuresEnabled) {
            ci.cancel();
            return;
        }
        XenoEntityStats.get().recordPhaseExecuted();
    }
}
