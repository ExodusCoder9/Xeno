package com.xeno.client.mixin;

import com.xeno.client.culling.XenoVertexFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(RenderPipeline.Builder.class)
@SuppressWarnings({"unused"})
public class RenderPipelineBuilderMixin {
    @Shadow
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<Identifier> location;

    @Final
    @Shadow
    private VertexFormat[] vertexFormatPerBuffer;

    @Inject(method = "build", at = @At("HEAD"))
    private void xeno_overrideTerrainVertexFormat(CallbackInfoReturnable<RenderPipeline> cir) {
        if (this.location.isPresent()) {
            String path = this.location.get().getPath();
            if (path.equals("pipeline/solid_terrain") || 
                path.equals("pipeline/cutout_terrain") || 
                path.equals("pipeline/translucent_terrain")) {
                this.vertexFormatPerBuffer[0] = XenoVertexFormat.XENO_COMPRESSED_FORMAT;
            }
        }
    }
}
