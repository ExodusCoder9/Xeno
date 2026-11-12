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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xeno.client.common.render.chunk.XenoSectionCompiler;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class XenoLevelRendererMixin {
    @ModifyExpressionValue(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/HintsAndWorkarounds;multiDrawIndirectHasKnownIssues()Z"
        )
    )
    private boolean xeno$ignoreKnownIssues(boolean original) {
        return false;
    }

    @WrapOperation(
        method = "invalidateCompiledGeometry",
        at = @At(value = "NEW", target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;")
    )
    private static SectionCompiler xeno$useXenoCompiler(
        boolean ambientOcclusion, boolean cutoutLeaves,
        BlockStateModelSet blockModelSet, FluidStateModelSet fluidModelSet, BlockColors blockColors,
        Operation<SectionCompiler> original
    ) {
        return new XenoSectionCompiler(ambientOcclusion, cutoutLeaves, blockModelSet, fluidModelSet, blockColors);
    }
}
