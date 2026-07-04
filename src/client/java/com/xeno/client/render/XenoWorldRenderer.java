package com.xeno.client.render;

import com.xeno.client.globals.XenoGlobals;
import com.xeno.client.render.buffer.UploadManager;
import com.xeno.client.render.buffer.StagingBuffer;
import com.xeno.client.render.chunk.ChunkBuilder;
import com.xeno.client.render.chunk.culling.CullingOutput;
import com.xeno.client.render.chunk.culling.CullingInput;
import com.xeno.client.render.chunk.culling.CullingThread;
import com.xeno.client.render.chunk.culling.Frustum;
import com.xeno.client.render.chunk.info.SectionInfoBuffer;
import com.xeno.client.render.chunk.storage.SectionStorage;
import com.xeno.client.render.chunk.storage.SectionState;
import com.xeno.client.render.chunk.storage.XenoSection;
import com.xeno.client.render.chunk.terrain.ChunkRenderList;
import com.xeno.client.render.chunk.terrain.RenderRegionManager;
import com.xeno.client.render.pipeline.RenderPassManager;
import com.xeno.client.render.pipeline.RenderPipelineManager;
import com.xeno.client.render.shader.ShaderManager;
import com.xeno.client.render.viewport.Viewport;
import com.xeno.client.render.viewport.CameraTransform;
import com.xeno.config.XenoConfig;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.concurrent.atomic.AtomicReference;

public class XenoWorldRenderer {
	private static XenoWorldRenderer INSTANCE;

	private final XenoConfig config = XenoConfig.INSTANCE;
	private final SectionStorage sectionStorage = new SectionStorage();
	private final ChunkBuilder chunkBuilder = new ChunkBuilder();
	private final RenderRegionManager regionManager = new RenderRegionManager();
	private final RenderPassManager renderPassManager = new RenderPassManager();
	private final UploadManager uploadManager = new UploadManager(new StagingBuffer(16 * 1024 * 1024));
	private final ChunkRenderList chunkRenderList = new ChunkRenderList();
	private final Viewport viewport = new Viewport();
	private final CameraTransform cameraTransform = new CameraTransform();

	private CullingThread cullingThread;
	private ShaderManager shaderManager;
	private RenderPipelineManager pipelineManager;

	private final AtomicReference<CullingOutput> cullingOutputRef = new AtomicReference<>();
	private long frameIndex;

	private XenoWorldRenderer() {}

	public static void initialize() {
		if (INSTANCE == null) {
			INSTANCE = new XenoWorldRenderer();
			XenoGlobals.worldRenderer = INSTANCE;
		}
	}

	public static XenoWorldRenderer getInstance() { return INSTANCE; }

	public void setupTerrain(Camera camera, Matrix4fc projectionMatrix, Matrix4fc viewMatrix) {
		if (!config.enableXenoTerrain) return;

		CullingOutput output = cullingOutputRef.getAndSet(null);
		if (output != null) {
			chunkRenderList.clear();
			for (XenoSection section : output.getVisibleSections()) {
				int rx = section.getX() >> 3;
				int ry = section.getY() >> 3;
				int rz = section.getZ() >> 3;
				long regionKey = RenderRegionManager.regionKey(rx, ry, rz);
				chunkRenderList.addSection(regionKey, regionManager.getOrCreateRegion(rx, ry, rz));
			}
			chunkRenderList.build();
		}
	}

	public void buildFrameGraph(FrameGraphBuilder frameGraph, RenderTarget mainTarget,
								GpuTextureView depthTexture) {
		if (!config.enableXenoTerrain) return;
		renderPassManager.buildTerrainPasses(
			frameGraph, mainTarget, depthTexture,
			chunkRenderList, chunkRenderList, chunkRenderList
		);
	}

	public void endFrame() {
		if (!config.enableXenoTerrain) return;
		chunkBuilder.processCompleted();
		uploadManager.flush(RenderSystem.getDevice());
	}

	public void postFrame() {
		frameIndex++;
	}

	public void submitCullingInput(Vec3 cameraPos, Matrix4f projection, Matrix4f view, float fov) {
		if (cullingThread == null || !config.asyncCulling) return;
		cullingThread.offerInput(new CullingInput(
			cameraPos, projection, view, fov, frameIndex
		));
	}

	public void onLevelChange() {
		sectionStorage.clear();
		regionManager.clear();
		chunkBuilder.clear();
		chunkRenderList.clear();
	}

	public void startCullingThread() {
		if (cullingThread != null) return;
		cullingThread = new CullingThread();
		cullingThread.setSectionStorage(sectionStorage);
		cullingThread.setName("Xeno-Cull-Thread");
		cullingThread.start();
	}

	public void stopCullingThread() {
		if (cullingThread != null) {
			cullingThread.shutdown();
			cullingThread = null;
		}
	}

	public SectionStorage getSectionStorage() { return sectionStorage; }
	public ChunkBuilder getChunkBuilder() { return chunkBuilder; }
	public RenderRegionManager getRegionManager() { return regionManager; }
	public ChunkRenderList getChunkRenderList() { return chunkRenderList; }
	public UploadManager getUploadManager() { return uploadManager; }
	public Viewport getViewport() { return viewport; }
	public CameraTransform getCameraTransform() { return cameraTransform; }
	public XenoConfig getConfig() { return config; }
}
