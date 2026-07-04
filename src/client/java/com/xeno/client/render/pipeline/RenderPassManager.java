package com.xeno.client.render.pipeline;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.xeno.client.render.XenoWorldRenderer;
import com.xeno.client.render.chunk.terrain.ChunkRenderList;
import com.xeno.client.render.chunk.storage.XenoSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import java.util.Optional;
import java.util.OptionalDouble;

public class RenderPassManager {
	public void buildTerrainPasses(
		FrameGraphBuilder frameGraph,
		RenderTarget mainTarget,
		GpuTextureView depthTexture,
		ChunkRenderList solidList,
		ChunkRenderList cutoutList,
		ChunkRenderList translucentList
	) {
		FramePass solidPass = frameGraph.addPass("xeno_solid");
		solidPass.executes(() -> {
			renderTerrainLayer(ChunkSectionLayer.SOLID, mainTarget, solidList);
		});

		FramePass cutoutPass = frameGraph.addPass("xeno_cutout");
		cutoutPass.executes(() -> {
			renderTerrainLayer(ChunkSectionLayer.CUTOUT, mainTarget, cutoutList);
		});

		FramePass translucentPass = frameGraph.addPass("xeno_translucent");
		translucentPass.executes(() -> {
			renderTerrainLayer(ChunkSectionLayer.TRANSLUCENT, mainTarget, translucentList);
		});
	}

	private void renderTerrainLayer(ChunkSectionLayer layer, RenderTarget renderTarget, ChunkRenderList renderList) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.levelRenderer == null || renderList.getRegionDrawLists().isEmpty()) return;

		GpuTextureView atlasView = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
		int atlasWidth = atlasView.getWidth(0);
		int atlasHeight = atlasView.getHeight(0);

		GpuSampler sampler = ((com.xeno.client.render.LevelRendererExt) mc.levelRenderer).getChunkLayerSampler();
		if (sampler == null) return;

		boolean wireframe = net.minecraft.SharedConstants.DEBUG_HOTKEYS && mc.wireframe;
		long now = Util.getMillis();
		Matrix4fc modelViewMatrix = RenderSystem.getModelViewMatrixCopy();

		try (RenderPass renderPass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				() -> "Xeno Section Layer " + layer.name(),
				renderTarget.getColorTextureView(),
				Optional.empty(),
				renderTarget.getDepthTextureView(),
				OptionalDouble.empty()
			)) {

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.bindTexture("Sampler0", atlasView, sampler);
			renderPass.bindTexture("Sampler2", mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			renderPass.setPipeline(wireframe ? RenderPipelines.WIREFRAME : layer.pipeline());

			for (ChunkRenderList.RegionDrawList rdl : renderList.getRegionDrawLists()) {
				for (XenoSection section : rdl.getVisibleSections()) {
					SectionMesh mesh = section.getVanillaSection().getSectionMesh();
					if (mesh != null && !mesh.isEmpty(layer)) {
						SectionRenderDispatcher.RenderSectionBufferSlice slice =
							mc.levelRenderer.sectionRenderDispatcher().getRenderSectionSlice(mesh, layer);
						if (slice != null) {
							SectionMesh.SectionDraw draw = mesh.getSectionDraw(layer);
							if (draw != null && (!draw.hasCustomIndexBuffer() || slice.indexBuffer() != null)) {
								BlockPos renderOrigin = section.getVanillaSection().getRenderOrigin();
								DynamicUniforms.ChunkSectionInfo info = new DynamicUniforms.ChunkSectionInfo(
									new Matrix4f(modelViewMatrix),
									renderOrigin.getX(),
									renderOrigin.getY(),
									renderOrigin.getZ(),
									section.getVanillaSection().getVisibility(now),
									atlasWidth,
									atlasHeight
								);
								GpuBufferSlice uboSlice = RenderSystem.getDynamicUniforms().writeChunkSections(info)[0];
								renderPass.setUniform("ChunkSection", uboSlice);

								renderPass.setVertexBuffer(0, slice.vertexBuffer().slice(slice.vertexBufferOffset(), slice.vertexBuffer().size() - slice.vertexBufferOffset()));
								if (slice.indexBuffer() != null) {
									renderPass.setIndexBuffer(slice.indexBuffer(), draw.indexType());
									renderPass.drawIndexed(
										draw.indexCount(),
										1,
										(int) (slice.indexBufferOffset() / draw.indexType().bytes),
										0,
										0
									);
								}
							}
						}
					}
				}
			}
		}
	}
}
