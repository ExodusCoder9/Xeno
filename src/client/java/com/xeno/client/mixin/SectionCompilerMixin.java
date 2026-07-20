package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import com.xeno.client.renderer.meshing.XenoSectionCompiler;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SectionCompiler.class, priority = 500)
public abstract class SectionCompilerMixin {
    @Shadow @Final private boolean ambientOcclusion;
    @Shadow @Final private boolean cutoutLeaves;
    @Shadow @Final private BlockStateModelSet blockModelSet;
    @Shadow @Final private FluidStateModelSet fluidModelSet;
    @Shadow @Final private BlockColors blockColors;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void xenoFastCompile(
            SectionPos sectionPos,
            RenderSectionRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        SectionCompiler.Results results = XenoSectionCompiler.compile(
                sectionPos,
                region,
                vertexSorting,
                builders,
                this.blockModelSet,
                this.fluidModelSet,
                this.blockColors,
                this.ambientOcclusion,
                this.cutoutLeaves
        );
        cir.setReturnValue(results);
    }
}