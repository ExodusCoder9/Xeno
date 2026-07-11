package com.xeno.client.mixin;

import com.mojang.blaze3d.systems.HintsAndWorkarounds;
import com.mojang.blaze3d.vertex.StagingBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StagingBuffer.class)
public class StagingBufferMixin {
    /**
     * @author ExodusCoder9
     * @reason Force persistently mapped staging buffers on all hardware that supports persistent mapping, bypassing the writeToBufferIsSlow check.
     */
    @Redirect(
        method = "create",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/HintsAndWorkarounds;writeToBufferIsSlow()Z"
        )
    )
    private static boolean xenoForcePersistentMapping(HintsAndWorkarounds instance) {
        return true;
    }
}
