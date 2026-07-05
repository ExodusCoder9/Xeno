package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoMesher;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
@SuppressWarnings("unused")
public class LevelRendererCompilerMixin {
    @Redirect(
        method = "invalidateCompiledGeometry",
        at = @At(
            value = "NEW",
            target = "(ZZLnet/minecraft/client/renderer/block/BlockStateModelSet;Lnet/minecraft/client/renderer/block/FluidStateModelSet;Lnet/minecraft/client/color/block/BlockColors;)Lnet/minecraft/client/renderer/chunk/SectionCompiler;"
        )
    )
    private SectionCompiler xeno_replaceSectionCompiler(
        boolean ambientOcclusion, boolean cutoutLeaves, BlockStateModelSet blockModelSet, FluidStateModelSet fluidModelSet, BlockColors blockColors
    ) {
        return new XenoMesher(ambientOcclusion, cutoutLeaves, blockModelSet, fluidModelSet, blockColors);
    }
}
