package com.xeno.client.render.chunk;

import com.xeno.client.render.chunk.compile.ChunkCompileTask;
import com.xeno.client.render.chunk.compile.ChunkCompileResult;
import com.xeno.client.render.chunk.compile.CompileTaskQueue;
import com.xeno.client.render.chunk.compile.SectionCompilerWrapper;
import com.xeno.util.XenoThreadUtil;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ChunkBuilder {
	private final ExecutorService compileExecutor;
	private final CompileTaskQueue pendingTasks;
	private final ConcurrentLinkedQueue<ChunkCompileResult> completedResults;
	private final ConcurrentLinkedQueue<Object> meshUpdates;
	private SectionCompilerWrapper sectionCompiler;
	private final long maxCompileTimePerFrame = 5_000_000;

	public ChunkBuilder() {
		this.compileExecutor = Executors.newFixedThreadPool(
			2, XenoThreadUtil.createThreadFactory("Xeno-Compile", false)
		);
		this.pendingTasks = new CompileTaskQueue();
		this.completedResults = new ConcurrentLinkedQueue<>();
		this.meshUpdates = new ConcurrentLinkedQueue<>();
	}

	public void submit(ChunkCompileTask task) {
		pendingTasks.offer(task);
	}

	public void processCompleted() {
	}

	public void drainMeshUpdates(Consumer<Object> consumer) {
		Object update;
		while ((update = meshUpdates.poll()) != null) {
			consumer.accept(update);
		}
	}

	public void clear() {
		pendingTasks.clear();
		completedResults.clear();
		meshUpdates.clear();
	}

	public void dispose() {
		compileExecutor.shutdown();
	}

	public CompileTaskQueue getPendingTasks() { return pendingTasks; }
	public ConcurrentLinkedQueue<ChunkCompileResult> getCompletedResults() { return completedResults; }
}
