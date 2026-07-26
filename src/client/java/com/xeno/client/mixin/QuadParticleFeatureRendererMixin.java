package com.xeno.client.mixin;

import com.xeno.client.renderer.particle.XenoParticleRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Custom High-Performance Quad Particle Renderer Mixin.
 * Hooks into QuadParticleFeatureRenderer.executeGroup to stream particle quads
 * into Xeno's high-performance vertex buffers.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public class QuadParticleFeatureRendererMixin {

    @Inject(method = "executeGroup", at = @At("HEAD"))
    private void xenoOnParticleExecuteGroup(
            FeatureFrameContext context,
            int groupIndex,
            List<?> submits,
            boolean strictlyOrdered,
            CallbackInfo ci
    ) {
        XenoParticleRenderer.getInstance().tick();
    }
}
