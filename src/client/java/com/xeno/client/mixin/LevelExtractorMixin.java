package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Inject(
        method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/LevelRenderer;)V",
        at = @At("RETURN")
    )
    private void xenoStoreExtractor(Minecraft minecraft, LevelRenderState levelRenderState, LevelRenderer levelRenderer, CallbackInfo ci) {
        XenoClient.setLevelExtractor((LevelExtractor) (Object) this);
    }
}
