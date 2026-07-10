package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void xeno$onRenderStart(
            com.mojang.blaze3d.resource.GraphicsResourceAllocator resourceAllocator,
            net.minecraft.client.DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            org.joml.Matrix4fc modelViewMatrix,
            com.mojang.blaze3d.buffers.GpuBufferSlice terrainFog,
            org.joml.Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci
    ) {
        XenoClient.setLevelRenderer((LevelRenderer) (Object) this);
        XenoClient.onRenderFrame(cameraState);
    }

    @Inject(method = "resetLevelRenderData", at = @At("HEAD"))
    private void xeno$onResetLevelRenderData(CallbackInfo ci) {
        XenoClient.setLevelRenderer(null);
    }
}
