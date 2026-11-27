package com.xeno.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.xeno.client.common.render.chunk.XenoSectionCompiler;
import com.xeno.client.common.render.culling.XenoOcclusionGraph;
import com.xeno.client.common.render.texture.XenoCompiledSectionMeshExtension;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class XenoLevelRendererMixin {
    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

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
        method = "<init>",
        at = @At(value = "NEW", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;")
    )
    private static SectionOcclusionGraph xeno$useXenoOcclusionGraph(Operation<SectionOcclusionGraph> original) {
        return new XenoOcclusionGraph();
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

    @Inject(method = {"prepareChunkRenders", "prepareChunkRendersIndirect"}, at = @At("RETURN"))
    private void xeno$markVisibleSectionSprites(CallbackInfoReturnable<?> cir) {
        ObjectArrayList<SectionRenderDispatcher.RenderSection> sections = this.visibleSections;
        for (int i = 0; i < sections.size(); i++) {
            SectionMesh mesh = sections.get(i).getSectionMesh();
            if (mesh instanceof XenoCompiledSectionMeshExtension ext) {
                ext.xeno$markAnimatedSpritesActive();
            }
        }
    }
}
