package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import com.xeno.client.culling.CullingThread;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Shadow @Final private LevelRenderer levelRenderer;

    @Inject(method = "applyFrustum", at = @At("HEAD"), cancellable = true)
    private void xeno$applyFrustum(Frustum frustum, CallbackInfo ci) {
        if (!XenoClient.isActive()) return;

        CullingThread thread = XenoClient.getCullingThread();
        if (thread == null) return;

        this.levelRenderer.clearVisibleSections();

        byte[] vis = thread.getResult().visibilityArray();

        if (XenoClient.getOctree() != null) {
            XenoClient.getOctree().visitVisible(
                    (section, fullyVisible, depth, isClose) -> {
                        this.levelRenderer.visibleSections().add(section);
                        if (isClose) {
                            this.levelRenderer.nearbyVisibleSections().add(section);
                        }
                    },
                    frustum,
                    vis,
                    32
            );
        }

        ci.cancel();
    }
}
