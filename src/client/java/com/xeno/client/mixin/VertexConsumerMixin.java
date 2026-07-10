package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexConsumer.class)
public interface VertexConsumerMixin {
    @Inject(method = "putBlockBakedQuad", at = @At("HEAD"), cancellable = true)
    default void xenoOnPutBlockBakedQuad(
            float x, float y, float z,
            BakedQuad quad,
            QuadInstance instance,
            CallbackInfo ci
    ) {
        Long currentSection = XenoClient.xenoGetCurrentSectionNode();
        if (currentSection != null) {
            net.minecraft.core.Direction dir = quad.direction();
            int dirOrdinal = dir.ordinal();
            XenoClient.xenoPerDirCounts.get()[dirOrdinal]++;

            if (XenoClient.xenoShouldCull.get()) {
                float[] cullDir = XenoClient.xenoCullDir.get();
                float dot = XenoClient.xenoDotProduct(dirOrdinal, cullDir[0], cullDir[1], cullDir[2]);
                if (dot < -0.2f) {
                    ci.cancel();
                    return;
                }
            }
            XenoClient.xenoTotalVertices.get()[0] += 4;
        }
    }
}
