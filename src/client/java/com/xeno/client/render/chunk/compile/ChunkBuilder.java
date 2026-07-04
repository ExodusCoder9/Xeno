package com.xeno.client.render.chunk.compile;

import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.xeno.client.render.chunk.vertex.VertexPacker;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkBuilder extends Thread {
	private final SectionCompilerWrapper compilerWrapper;
	private final CompileTaskQueue taskQueue;
	private final Queue<ChunkCompileResult> results = new ConcurrentLinkedQueue<>();
	private final RenderSectionRegion region;
	private volatile Vec3 cameraPos = Vec3.ZERO;
	private volatile boolean running;

	public ChunkBuilder(SectionCompilerWrapper compilerWrapper, CompileTaskQueue taskQueue, RenderSectionRegion region) {
		super("Xeno-ChunkBuilder");
		this.compilerWrapper = compilerWrapper;
		this.taskQueue = taskQueue;
		this.region = region;
	}

	public void setCameraPosition(Vec3 pos) {
		this.cameraPos = pos;
	}

	@Override
	public void run() {
		while (running) {
			try {
				ChunkCompileTask task = taskQueue.take();
				compile(task);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void compile(ChunkCompileTask task) {
		try {
			SectionPos sectionPos = task.sectionPos();
			Vec3 cam = cameraPos;
			VertexSorting sorting = VertexSorting.byDistance(
				(float)(cam.x - sectionPos.minBlockX()),
				(float)(cam.y - sectionPos.minBlockY()),
				(float)(cam.z - sectionPos.minBlockZ())
			);
			SectionBufferBuilderPack pack = new SectionBufferBuilderPack();
			SectionCompiler.Results vanillaResults = null;
			List<ChunkCompileResult.LayerResult> layerResults = new ArrayList<>();
			try {
				vanillaResults = compilerWrapper.compile(sectionPos, region, sorting, pack);
				for (Map.Entry<ChunkSectionLayer, MeshData> entry : vanillaResults.renderedLayers.entrySet()) {
					ChunkSectionLayer layer = entry.getKey();
					MeshData meshData = entry.getValue();
					if (meshData != null && meshData.drawState().vertexCount() > 0) {
						ByteBuffer packedVertices = packVertexBuffer(meshData);
						ByteBuffer vanillaIndices = meshData.indexBuffer();
						ByteBuffer packedIndices = null;
						if (vanillaIndices != null) {
							packedIndices = ByteBuffer.allocateDirect(vanillaIndices.remaining());
							packedIndices.order(java.nio.ByteOrder.nativeOrder());
							packedIndices.put(vanillaIndices.duplicate());
							packedIndices.flip();
						}
						layerResults.add(new ChunkCompileResult.LayerResult(
							layer,
							packedVertices,
							packedIndices,
							meshData.drawState().vertexCount(),
							meshData.drawState().indexCount()
						));
					}
				}
			} finally {
				if (vanillaResults != null) {
					vanillaResults.release();
				}
				pack.discardAll();
			}

			results.offer(new ChunkCompileResult(
				task.sectionKey(),
				layerResults.toArray(new ChunkCompileResult.LayerResult[0]),
				true
			));
		} catch (Exception e) {
			results.offer(new ChunkCompileResult(
				task.sectionKey(),
				new ChunkCompileResult.LayerResult[0],
				false
			));
		}
	}

	private ByteBuffer packVertexBuffer(MeshData meshData) {
		ByteBuffer src = meshData.vertexBuffer();
		int vertexCount = meshData.drawState().vertexCount();
		VertexFormat format = meshData.drawState().format();
		int stride = format.getVertexSize();

		VertexFormatElement posEl = format.getElement("Position");
		VertexFormatElement colorEl = format.getElement("Color");
		VertexFormatElement uvEl = format.getElement("UV0");
		VertexFormatElement lightEl = format.getElement("UV2");

		int posOffset = posEl != null ? posEl.offset() : 0;
		int colorOffset = colorEl != null ? colorEl.offset() : 12;
		int uvOffset = uvEl != null ? uvEl.offset() : 16;
		int lightOffset = lightEl != null ? lightEl.offset() : 24;

		ByteBuffer dest = ByteBuffer.allocateDirect(vertexCount * 16);
		dest.order(java.nio.ByteOrder.nativeOrder());

		for (int i = 0; i < vertexCount; i++) {
			int vOffset = i * stride;
			
			float x = src.getFloat(vOffset + posOffset);
			float y = src.getFloat(vOffset + posOffset + 4);
			float z = src.getFloat(vOffset + posOffset + 8);

			int r = 255, g = 255, b = 255, a = 255;
			if (colorEl != null) {
				r = src.get(vOffset + colorOffset) & 0xFF;
				g = src.get(vOffset + colorOffset + 1) & 0xFF;
				b = src.get(vOffset + colorOffset + 2) & 0xFF;
				a = src.get(vOffset + colorOffset + 3) & 0xFF;
			}

			float u = 0, v = 0;
			if (uvEl != null) {
				u = src.getFloat(vOffset + uvOffset);
				v = src.getFloat(vOffset + uvOffset + 4);
			}

			int blockLight = 0, skyLight = 0;
			if (lightEl != null) {
				int lightmap = src.getInt(vOffset + lightOffset);
				blockLight = (lightmap & 0xFFFF) >> 4;
				skyLight = ((lightmap >> 16) & 0xFFFF) >> 4;
			}

			VertexPacker.writeVertex(
				dest,
				x, y, z,
				u, v,
				r, g, b, a,
				blockLight, skyLight,
				3, 0, 0
			);
		}

		dest.flip();
		return dest;
	}

	public void start() {
		running = true;
		super.start();
	}

	public void stopBuilder() {
		running = false;
		interrupt();
	}

	public void processCompleted() {
		ChunkCompileResult result;
		while ((result = results.poll()) != null) {
		}
	}

	public ChunkCompileResult pollCompleted() {
		return results.poll();
	}

	public void submit(long sectionKey, int x, int y, int z, int priority) {
		taskQueue.offer(new ChunkCompileTask(sectionKey, SectionPos.of(x, y, z), priority));
	}

	public void clear() {
		taskQueue.clear();
		results.clear();
	}
}
