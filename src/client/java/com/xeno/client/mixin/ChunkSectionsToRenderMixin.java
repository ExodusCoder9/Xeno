package com.xeno.client.mixin;

import com.xeno.config.XenoConfig;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderMixin {
	@Inject(
		method = "renderGroup",
		at = @At("HEAD"),
		cancellable = true
	)
	private void xeno_cancelRenderGroup(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			ci.cancel();
		}
	}
}
