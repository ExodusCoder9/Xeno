package com.xeno.client.mixin;

import com.mojang.logging.LogUtils;
import com.xeno.client.XenoClient;
import com.xeno.client.culling.CullingThread;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Unique private static final Logger XENO_LOGGER = LogUtils.getLogger();
    @Unique private static boolean hasLoggedFirstApply;

    @Shadow @Final private LevelRenderer levelRenderer;

    @Inject(method = "applyFrustum", at = @At("HEAD"), cancellable = true)
    private void xeno$applyFrustum(Frustum frustum, CallbackInfo ci) {
        if (!XenoClient.isActive()) return;

        var octree = XenoClient.getOctree();
        if (octree == null) return;

        CullingThread thread = XenoClient.getCullingThread();
        if (thread == null) return;

        this.levelRenderer.clearVisibleSections();

        byte[] vis = thread.getResult().visibilityArray();
        int[] count = {0};

        octree.visitVisible(
                (section, _, _, isClose) -> {
                    this.levelRenderer.visibleSections().add(section);
                    count[0]++;
                    if (isClose) {
                        this.levelRenderer.nearbyVisibleSections().add(section);
                    }
                },
                frustum,
                vis,
                32
        );

        if (count[0] > 0 && !hasLoggedFirstApply) {
            XENO_LOGGER.info("[Xeno] applyFrustum: added {} visible sections", count[0]);
            hasLoggedFirstApply = true;
        } else if (count[0] == 0) {
            XENO_LOGGER.warn("[Xeno] applyFrustum: ZERO sections added!");
        }

        ci.cancel();
    }
}
