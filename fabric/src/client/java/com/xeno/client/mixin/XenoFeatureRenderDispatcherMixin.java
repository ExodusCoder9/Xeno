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

import com.xeno.fabric.client.render.frapi.render.XenoExtendedBlockModelRenderer;
import com.xeno.fabric.client.render.frapi.render.XenoExtendedItemRenderer;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedItemSubmit;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public abstract class XenoFeatureRenderDispatcherMixin {
    @Shadow
    @Final
    private FeatureRendererMap featureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xeno$registerExtendedFeatureRenderers(CallbackInfo ci) {
        this.featureRenderers.put(
            ExtendedBlockModelSubmit.TYPE,
            new XenoExtendedBlockModelRenderer()
        );
        this.featureRenderers.put(
            ExtendedItemSubmit.TYPE,
            new XenoExtendedItemRenderer()
        );
    }
}
