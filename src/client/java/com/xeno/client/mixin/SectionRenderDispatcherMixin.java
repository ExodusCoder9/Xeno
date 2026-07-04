package com.xeno.client.mixin;

import com.xeno.client.culling.XenoTaskQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.class)
@SuppressWarnings({"unused"})
public class SectionRenderDispatcherMixin {
    @Shadow @Final
    private SectionTaskDynamicQueue queue;

    @Inject(method = "setCameraPosition", at = @At("HEAD"))
    private void xeno_updateCameraLook(Vec3 cameraPosition, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Vector3fc look = mc.gameRenderer.mainCamera().forwardVector();
        ((XenoTaskQueue) this.queue).xeno_updateCameraLook(look.x(), look.y(), look.z());
    }
}
