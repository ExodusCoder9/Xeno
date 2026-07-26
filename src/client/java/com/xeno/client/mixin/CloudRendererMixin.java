package com.xeno.client.mixin;

import com.xeno.client.renderer.world.XenoCloudRenderer;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Cloud Renderer optimization mixin.
 * Intercepts cloud mesh builds and invalidation to delegate to XenoCloudRenderer.
 */
@Mixin(CloudRenderer.class)
public class CloudRendererMixin {

    @Inject(method = "markForRebuild", at = @At("HEAD"), cancellable = true)
    private void xenoOnCloudRebuild(CallbackInfo ci) {
        ci.cancel();
        XenoCloudRenderer.getInstance().markForRebuild();
    }
}
