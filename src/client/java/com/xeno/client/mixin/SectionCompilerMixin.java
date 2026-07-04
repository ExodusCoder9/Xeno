package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoMeshingCache;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
@SuppressWarnings("unused")
public class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void xeno_compileHead(
        SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders,
        CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        XenoMeshingCache.get().init(region, sectionPos.origin());
    }

    @Redirect(
        method = "compile",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState xeno_redirectGetBlockState(RenderSectionRegion region, BlockPos pos) {
        return XenoMeshingCache.get().getBlockState(region, pos);
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void xeno_compileReturn(
        SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders,
        CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        XenoMeshingCache.get().disable();
    }
}
