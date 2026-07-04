package com.xeno.client.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import com.xeno.client.renderer.XenoMdiRenderer;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.List;

@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderMixin {
    @SuppressWarnings("unchecked")
    @Redirect(
        method = "renderGroup",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;drawMultipleIndexed(Ljava/util/Collection;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;Ljava/util/Collection;Ljava/lang/Object;)V"
        )
    )
    private <T> void redirect_drawMultipleIndexed(
        RenderPass renderPass,
        Collection<RenderPass.Draw<T>> draws,
        GpuBuffer defaultIndexBuffer,
        IndexType defaultIndexType,
        Collection<String> dynamicUniforms,
        T uniformArgument
    ) {
        // Try Multi-Draw Indirect (MDI) chunk rendering
        if (XenoMdiRenderer.tryRenderMdi(
            renderPass,
            (List<RenderPass.Draw<GpuBufferSlice[]>>) (Object) draws,
            defaultIndexBuffer,
            defaultIndexType
        )) {
            return; // Successfully rendered using modern MDI!
        }

        // Fallback to vanilla multi-draw list path
        renderPass.drawMultipleIndexed(draws, defaultIndexBuffer, defaultIndexType, dynamicUniforms, uniformArgument);
    }

    @Inject(method = "renderGroup", at = @At("TAIL"))
    private void cleanup_renderGroup(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        XenoMdiRenderer.CURRENT_SECTION_INFOS.remove();
    }
}
