package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderPipelines.class)
public class RenderPipelinesMixin {
    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;withVertexShader(Ljava/lang/String;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"
        )
    )
    private static RenderPipeline.Builder xenoRedirectVertexShader(RenderPipeline.Builder builder, String shader) {
        if ("core/terrain".equals(shader)) {
            return builder.withVertexShader(shader).withVertexBinding(0, XenoClient.COMPRESSED_BLOCK_FORMAT);
        }
        return builder.withVertexShader(shader);
    }
}
