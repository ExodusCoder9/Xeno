package com.xeno.client.mixin;

import com.xeno.client.renderer.chunk.XenoSectionCompiler;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import com.mojang.blaze3d.vertex.VertexSorting;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin delegating SectionCompiler.compile directly to XenoSectionCompiler.
 * ZERO business logic in Mixin.
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {

    @Shadow @Final private boolean ambientOcclusion;
    @Shadow @Final private boolean cutoutLeaves;
    @Shadow @Final private BlockStateModelSet blockModelSet;
    @Shadow @Final private FluidStateModelSet fluidModelSet;
    @Shadow @Final private BlockColors blockColors;

    @Unique
    private XenoSectionCompiler xeno$delegateCompiler;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void onCompile(SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        if (this.xeno$delegateCompiler == null) {
            this.xeno$delegateCompiler = new XenoSectionCompiler(
                    this.ambientOcclusion,
                    this.cutoutLeaves,
                    this.blockModelSet,
                    this.fluidModelSet,
                    this.blockColors
            );
        }

        SectionCompiler.Results results = this.xeno$delegateCompiler.compile(
                sectionPos,
                region,
                vertexSorting,
                builders
        );

        cir.setReturnValue(results);
    }
}