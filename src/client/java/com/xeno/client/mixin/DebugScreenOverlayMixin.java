package com.xeno.client.mixin;

import com.xeno.client.culling.CullingThread;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Locale;

@Mixin(DebugScreenOverlay.class)
@SuppressWarnings("unused")
public class DebugScreenOverlayMixin {
    @Shadow @Final
    private Minecraft minecraft;

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
            boolean rendererEnabled = this.minecraft.debugEntries.isCurrentlyEnabled(Identifier.withDefaultNamespace("xeno_renderer"));
            boolean cullingEnabled = this.minecraft.debugEntries.isCurrentlyEnabled(Identifier.withDefaultNamespace("xeno_culling_stats"));

            int insertIndex = 0;
            boolean addedAny = false;

            if (rendererEnabled) {
                leftLines.add(insertIndex++, "\u00a7dXenoRenderer-2.2.1+mc26.2");
                addedAny = true;
            }
            if (cullingEnabled) {
                leftLines.add(insertIndex++, "\u00a7fAsynchronous Culling");
                leftLines.add(insertIndex++, String.format(Locale.ROOT, "\u00a77- Cull thread latency: \u00a7f%.2fms", CullingThread.displayedLatencyMs));
                leftLines.add(insertIndex++, String.format(Locale.ROOT, "\u00a77- Cull thread usage: \u00a7f%.1f%%", CullingThread.profiledUsagePercent));
                addedAny = true;
            }
            if (addedAny) {
                leftLines.add(insertIndex, ""); // Empty spacing line
            }
        }
    }
}
