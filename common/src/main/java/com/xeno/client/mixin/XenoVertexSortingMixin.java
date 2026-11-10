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

import com.mojang.blaze3d.vertex.VertexSorting;
import com.xeno.client.common.render.chunk.XenoDistanceSorting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexSorting.class)
public interface XenoVertexSortingMixin {
    @Inject(method = "byDistance(FFF)Lcom/mojang/blaze3d/vertex/VertexSorting;", at = @At("HEAD"), cancellable = true)
    private static void xeno$onByDistance(float x, float y, float z, CallbackInfoReturnable<VertexSorting> cir) {
        cir.setReturnValue(new XenoDistanceSorting(x, y, z));
    }
}
