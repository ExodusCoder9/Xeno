package com.xeno.client.render.chunk.compile;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CompileTaskQueue {
	private final BlockingQueue<ChunkCompileTask> queue = new LinkedBlockingQueue<>();

	public void offer(ChunkCompileTask task) {
		queue.offer(task);
	}

	public ChunkCompileTask take() throws InterruptedException {
		return queue.take();
	}

	public ChunkCompileTask poll() {
		return queue.poll();
	}

	public int size() {
		return queue.size();
	}

	public void clear() {
		queue.clear();
	}
}
