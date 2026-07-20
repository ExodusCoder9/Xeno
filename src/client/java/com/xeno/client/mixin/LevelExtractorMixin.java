package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import com.xeno.client.renderer.entity.IXenoEntityCuller;
import com.xeno.client.renderer.entity.XenoEntityCuller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private LevelRenderer levelRenderer;
    @Shadow private @org.jspecify.annotations.Nullable ClientLevel level;

    @Unique
    private final IXenoEntityCuller xeno$entityCuller = new XenoEntityCuller();

    @Inject(
            method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/LevelRenderer;)V",
            at = @At("RETURN")
    )
    private void xenoStoreExtractor(Minecraft minecraft, LevelRenderState levelRenderState, LevelRenderer levelRenderer, CallbackInfo ci) {
        XenoClient.setLevelExtractor((LevelExtractor) (Object) this);
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void xenoIsEntityVisible(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        cir.setReturnValue(this.xeno$entityCuller.isEntityVisible(
                entity, frustum, camX, camY, camZ, this.level, this.levelRenderer, this.minecraft
        ));
    }
}
