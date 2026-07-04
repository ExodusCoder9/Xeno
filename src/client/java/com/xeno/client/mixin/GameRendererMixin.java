package com.xeno.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererMixin {
	@Accessor("levelProjectionMatrixBuffer")
	ProjectionMatrixBuffer getLevelProjectionMatrixBuffer();
}
