package com.xeno.client.mixin;

import com.xeno.client.renderer.chunk.XenoSectionStorage;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts ClientChunkCache drop calls to manage XenoSectionStorage VRAM lifecycles.
 */
@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {

    @Inject(method = "drop", at = @At("HEAD"))
    private void xenoOnChunkDrop(ChunkPos pos, CallbackInfo ci) {
        if (pos != null) {
            XenoSectionStorage.getInstance().onChunkUnload(pos.x(), pos.z());
        }
    }
}
