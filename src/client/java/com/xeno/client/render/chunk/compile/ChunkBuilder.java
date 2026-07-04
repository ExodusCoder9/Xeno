package com.xeno.client.render.chunk.compile;

import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

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
			SectionCompiler.Results vanillaResults;
			try {
				vanillaResults = compilerWrapper.compile(sectionPos, region, sorting, pack);
			} finally {
				pack.discardAll();
			}
			boolean hasGeometry = !vanillaResults.renderedLayers.isEmpty();
			results.offer(new ChunkCompileResult(
				task.sectionKey(),
				null,
				null,
				0,
				0,
				0,
				hasGeometry ? 0 : 1,
				true
			));
		} catch (Exception e) {
			results.offer(new ChunkCompileResult(
				task.sectionKey(), null, null, 0, 0, 0, 0, false
			));
		}
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
