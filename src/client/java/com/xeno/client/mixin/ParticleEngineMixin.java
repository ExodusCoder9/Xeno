package com.xeno.client.mixin;

import com.xeno.client.renderer.particle.XenoParticleRenderer;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Particle Engine optimization mixin.
 * Delegates particle tick state to XenoParticleRenderer.
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void xenoOnParticleTick(CallbackInfo ci) {
        XenoParticleRenderer.getInstance().tick();
    }
}
