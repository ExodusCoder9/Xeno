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

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.client.common.render.entity.XenoBufferWriter;
import com.xeno.client.common.render.font.XenoFontRenderer;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedSheetGlyph.class)
public abstract class XenoBakedGlyphMixin {

    @Shadow @Final private float left;
    @Shadow @Final private float right;
    @Shadow @Final private float up;
    @Shadow @Final private float down;
    @Shadow @Final private float u0;
    @Shadow @Final private float u1;
    @Shadow @Final private float v0;
    @Shadow @Final private float v1;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(
        boolean italic,
        float x,
        float y,
        float z,
        Matrix4fc pose,
        VertexConsumer builder,
        int color,
        boolean bold,
        int packedLightCoords,
        CallbackInfo ci
    ) {
        if (builder instanceof XenoBufferWriter xbw && xbw.xeno$isGlyphFormat()) {
            float shearTop = 1.0F - 0.25F * this.up;
            float shearBottom = 1.0F - 0.25F * this.down;

            XenoFontRenderer.renderGlyph(
                xbw,
                pose,
                this.left, this.right, this.up, this.down,
                shearTop, shearBottom,
                italic, bold,
                x, y, z,
                this.u0, this.u1, this.v0, this.v1,
                color,
                packedLightCoords
            );
            ci.cancel();
        }
    }
}
