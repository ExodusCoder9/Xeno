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

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xeno.client.common.render.XenoRenderPipelines;
import com.xeno.client.common.render.chunk.XenoVertexFormats;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSectionLayer.class)
public abstract class XenoChunkSectionLayerMixin {
    @Inject(method = "pipeline", at = @At("HEAD"), cancellable = true)
    private void xeno$overridePipeline(CallbackInfoReturnable<RenderPipeline> cir) {
        ChunkSectionLayer layer = (ChunkSectionLayer) (Object) this;
        cir.setReturnValue(XenoRenderPipelines.getPipeline(layer, false));
    }

    @Inject(method = "vertexFormat", at = @At("HEAD"), cancellable = true)
    private void xeno$overrideVertexFormat(CallbackInfoReturnable<VertexFormat> cir) {
        cir.setReturnValue(XenoVertexFormats.TERRAIN);
    }
}
