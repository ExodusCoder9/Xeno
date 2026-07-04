package com.xeno.client.mixin;

import com.xeno.client.culling.CullingThread;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Locale;

@Mixin(DebugScreenOverlay.class)
@SuppressWarnings("unused")
public class DebugScreenOverlayMixin {
    @Inject(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;Z)V",
            ordinal = 0
        )
    )
    private void xeno_injectDebugLines(GuiGraphicsExtractor graphics, CallbackInfo ci, @Local(ordinal = 0) List<String> leftLines) {
        if (leftLines != null) {
            leftLines.add(0, "\u00a7dXenoRenderer-2.2.1+mc26.2");
            leftLines.add(1, "\u00a7fAsynchronous Culling");
            leftLines.add(2, String.format(Locale.ROOT, "\u00a77- Cull thread latency: \u00a7f%.2fms", CullingThread.displayedLatencyMs));
            leftLines.add(3, String.format(Locale.ROOT, "\u00a77- Cull thread usage: \u00a7f%.1f%%", CullingThread.profiledUsagePercent));
            leftLines.add(4, ""); // Empty spacing line
        }
    }
}
