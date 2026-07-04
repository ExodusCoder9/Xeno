package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoMeshingCache;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelLighter.Cache.class)
@SuppressWarnings("unused")
public class BlockModelLighterCacheMixin {
    @Inject(method = "getLightCoords", at = @At("HEAD"), cancellable = true)
    private void xeno_getLightCoords(BlockState state, BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        XenoMeshingCache cache = XenoMeshingCache.get();
        if (cache.active) {
            cir.setReturnValue(cache.getLightCoords((BlockModelLighter.Cache) (Object) this, level, state, pos));
        }
    }

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void xeno_getShadeBrightness(BlockState state, BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        XenoMeshingCache cache = XenoMeshingCache.get();
        if (cache.active) {
            cir.setReturnValue(cache.getShadeBrightness((BlockModelLighter.Cache) (Object) this, level, state, pos));
        }
    }
}
