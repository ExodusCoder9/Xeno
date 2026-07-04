package com.xeno.client.mixin;

import com.xeno.client.render.XenoWorldRenderer;
import com.xeno.config.XenoConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Vector4f;
import org.joml.Matrix4fc;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Consumer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin implements com.xeno.client.render.LevelRendererExt {

	@Shadow
	private Minecraft minecraft;

	@Shadow
	private com.mojang.blaze3d.textures.GpuSampler chunkLayerSampler;

	@Override
	public com.mojang.blaze3d.textures.GpuSampler getChunkLayerSampler() {
		return chunkLayerSampler;
	}

	@Inject(
		method = "render",
		at = @At("HEAD")
	)
	private void xeno_setupTerrain(
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
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			XenoWorldRenderer.getInstance().setupTerrain(
				this.minecraft.gameRenderer.mainCamera(),
				cameraState.projectionMatrix,
				cameraState.viewRotationMatrix
			);
		}
	}

	@Inject(
		method = "render",
		at = @At("TAIL")
	)
	private void xeno_submitCullingInput(
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
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			XenoWorldRenderer.getInstance().submitCullingInput(
				cameraState.pos,
				cameraState.projectionMatrix,
				cameraState.viewRotationMatrix,
				cameraState.hudFov
			);
			XenoWorldRenderer.getInstance().postFrame();
		}
	}

	@Inject(
		method = "addMainPass",
		at = @At("HEAD")
	)
	private void xeno_addMainPass(
		com.mojang.blaze3d.framegraph.FrameGraphBuilder frame,
		net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame featureFrame,
		com.mojang.blaze3d.buffers.GpuBufferSlice terrainFog,
		net.minecraft.client.renderer.state.level.LevelRenderState levelRenderState,
		net.minecraft.world.level.dimension.DimensionType.Skybox skybox,
		boolean renderOutline,
		boolean shouldRenderSky,
		org.joml.Vector4f fogColor,
		CallbackInfo ci
	) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			com.mojang.blaze3d.pipeline.RenderTarget mainTarget = this.minecraft.gameRenderer.mainRenderTarget();
			XenoWorldRenderer.getInstance().buildFrameGraph(frame, mainTarget, mainTarget.getDepthTextureView());
		}
	}

	@Inject(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;compileSections(Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"),
		cancellable = true
	)
	private void xeno_cancelCompileSections(
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
	private void xeno_cancelUploadTerrainBuffers(
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
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			ci.cancel();
		}
	}

	@Inject(
		method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"),
		cancellable = true
	)
	private void xeno_cancelOcclusionUpdate(
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
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;<init>(Lnet/minecraft/TracingExecutor;Lnet/minecraft/client/renderer/RenderBuffers;Lnet/minecraft/client/renderer/chunk/SectionCompiler;Ljava/util/function/Consumer;)V"),
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
	private void xeno_cancelAlwaysOnTop(
		com.mojang.blaze3d.framegraph.FrameGraphBuilder frame,
		net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame featureFrame,
		com.mojang.blaze3d.buffers.GpuBufferSlice fog,
		CallbackInfo ci
	) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) ci.cancel();
	}

	@Inject(
		method = "visibleSections",
		at = @At("HEAD"),
		cancellable = true
	)
	private void xeno_visibleSections(CallbackInfoReturnable<ObjectArrayList<SectionRenderDispatcher.RenderSection>> cir) {
		if (XenoConfig.INSTANCE.enableXenoTerrain) {
			cir.setReturnValue(XenoWorldRenderer.getInstance().getVisibleVanillaSections());
		}
	}
}
