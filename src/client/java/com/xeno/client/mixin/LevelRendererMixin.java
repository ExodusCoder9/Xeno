package com.xeno.client.mixin;

import com.xeno.client.render.XenoWorldRenderer;
import com.xeno.config.XenoConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

	@Inject(
		method = "render",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V")
	)
	private void xeno_buildXenoFrameGraph(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			XenoWorldRenderer.getInstance().buildFrameGraph(null, null, null);
		}
	}

	@Inject(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;compileSections(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"),
		cancellable = true
	)
	private void xeno_cancelCompileSections(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			XenoWorldRenderer.getInstance().endFrame();
			ci.cancel();
		}
	}

	@Inject(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"),
		cancellable = true
	)
	private void xeno_cancelUploadTerrainBuffers(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			ci.cancel();
		}
	}

	@Inject(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"),
		cancellable = true
	)
	private void xeno_cancelOcclusionUpdate(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			ci.cancel();
		}
	}

	@Inject(
		method = "invalidateCompiledGeometry",
		at = @At("TAIL")
	)
	private void xeno_initXenoWorldRenderer(ClientLevel level, Options options, Camera camera, BlockColors blockColors, CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			XenoWorldRenderer.getInstance().onLevelChange();
		}
	}

	@ModifyArg(
		method = "invalidateCompiledGeometry",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;<init>(Lnet/minecraft/client/renderer/TracingExecutor;Lnet/minecraft/client/renderer/RenderBuffers;Lnet/minecraft/client/renderer/chunk/SectionCompiler;Ljava/util/function/Consumer;)V"),
		index = 3
	)
	private Consumer<SectionRenderDispatcher.RenderSection> xeno_routeMeshUpdate(Consumer<SectionRenderDispatcher.RenderSection> callback) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			return section -> {};
		}
		return callback;
	}

	@Inject(
		method = "addAlwaysOnTopPass",
		at = @At("HEAD"),
		cancellable = true
	)
	private void xeno_cancelAlwaysOnTop(CallbackInfo ci) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}
}
