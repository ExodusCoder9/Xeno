package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xeno.client.renderer.XenoEntityStats;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererEntityMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void xeno_resetEntityStats(
        GraphicsResourceAllocator resourceAllocator,
        DeltaTracker deltaTracker,
        boolean renderOutline,
        CameraRenderState cameraState,
        Matrix4fc modelViewMatrix,
        GpuBufferSlice terrainFog,
        Vector4f fogColor,
        boolean shouldRenderSky,
        CallbackInfo ci
    ) {
        XenoEntityStats.resetFrame();
    }

    @Inject(method = "submitEntities", at = @At("HEAD"), cancellable = true)
    private void xeno_interceptEntitySubmit(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
        XenoEntityStats.FrameStats stats = XenoEntityStats.get();
        stats.entitiesSubmitted = levelRenderState.entityRenderStates.size();

        if (!stats.featuresEnabled) {
            ci.cancel();
        }
    }
}
