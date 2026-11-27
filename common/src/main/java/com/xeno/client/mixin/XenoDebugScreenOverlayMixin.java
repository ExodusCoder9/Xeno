/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.xeno.client.common.debug.XenoDebugEntry;
import com.xeno.client.common.debug.XenoFrameTimeStatistics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class XenoDebugScreenOverlayMixin {
    @Inject(method = "logFrameDuration", at = @At("HEAD"))
    private void xeno$logFrameDuration(long frameDuration, CallbackInfo ci) {
        XenoFrameTimeStatistics.INSTANCE.logSample(frameDuration);
    }

    @Inject(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;ZI)V",
            ordinal = 0
        )
    )
    private void xeno$insertFpsPercentiles(
            GuiGraphicsExtractor graphics,
            CallbackInfo ci,
            @Local(ordinal = 0) List<String> leftLines
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.debugEntries.isCurrentlyEnabled(XenoDebugEntry.FPS_PERCENTILES)) {
            return;
        }
        XenoFrameTimeStatistics stats = XenoFrameTimeStatistics.INSTANCE;
        if (!stats.hasSamples()) {
            return;
        }
        stats.updateIfNeeded();

        int insertIndex = 0;
        for (int i = 0; i < leftLines.size(); i++) {
            String line = leftLines.get(i);
            if (line != null && line.contains(" fps T:")) {
                insertIndex = i + 1;
                break;
            }
        }

        String formatted = ChatFormatting.GRAY + "p50=" + ChatFormatting.RESET + stats.p50Fps()
                + " " + ChatFormatting.GRAY + "p98=" + ChatFormatting.RESET + stats.p98Fps()
                + " " + ChatFormatting.GRAY + "p99.5=" + ChatFormatting.RESET + stats.p995Fps()
                + ChatFormatting.GRAY + " fps";
        leftLines.add(insertIndex, formatted);
    }
}
