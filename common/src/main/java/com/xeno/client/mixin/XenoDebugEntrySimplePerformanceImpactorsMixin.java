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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntrySimplePerformanceImpactors;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(DebugEntrySimplePerformanceImpactors.class)
public abstract class XenoDebugEntrySimplePerformanceImpactorsMixin {
    @Inject(
        method = "display",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isChunkRenderingUsingMultiDrawIndirect()Z"),
        cancellable = true
    )
    private void xeno$overrideTerrainRenderingLine(
        DebugScreenDisplayer displayer,
        @Nullable Level serverOrClientLevel,
        @Nullable LevelChunk clientChunk,
        @Nullable LevelChunk serverChunk,
        CallbackInfo ci
    ) {
        ci.cancel();
        boolean isMultiDrawIndirect = Minecraft.getInstance().levelRenderer.isChunkRenderingUsingMultiDrawIndirect();
        String backendName;
        if (isMultiDrawIndirect) {
            String deviceBackend = RenderSystem.getDevice().getDeviceInfo().backendName();
            if (deviceBackend != null && deviceBackend.equalsIgnoreCase("Vulkan")) {
                backendName = "Multi Draw VK (Xeno)";
            } else {
                backendName = "Multi Draw GL (Xeno)";
            }
        } else {
            backendName = "naive";
        }
        displayer.addLine(String.format(Locale.ROOT, "Terrain Rendering: %s", backendName));
    }
}
