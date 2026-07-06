package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoMdiRenderer;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
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
        if (draws.isEmpty()) return;

        GpuBufferSlice[] chunkSectionInfos = ((ChunkSectionsToRender) (Object) this).chunkSectionInfos();

        XenoMdiRenderer.renderDirect(
            renderPass,
            (List<RenderPass.Draw<GpuBufferSlice[]>>) (Object) draws,
            defaultIndexBuffer,
            defaultIndexType,
            chunkSectionInfos
        );
    }
}
