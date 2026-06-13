package com.xeno.mixin.render;

import net.minecraft.client.renderer.DynamicUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DynamicUniforms.class)
public class DynamicUniformsMixin {
    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniformStorage;<init>(Ljava/lang/String;II)V",
            ordinal = 0
        ),
        index = 2
    )
    private int increaseTransformCapacity(int capacity) {
        return 512;
    }

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniformStorage;<init>(Ljava/lang/String;II)V",
            ordinal = 1
        ),
        index = 2
    )
    private int increaseChunkSectionCapacity(int capacity) {
        return 512;
    }
}
