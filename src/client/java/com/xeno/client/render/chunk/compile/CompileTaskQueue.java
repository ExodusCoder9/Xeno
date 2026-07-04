package com.xeno.client.render.chunk.compile;

import java.util.PriorityQueue;

public class CompileTaskQueue {
	private final PriorityQueue<ChunkCompileTask> queue = new PriorityQueue<>();
	private static final int MAX_QUEUE_SIZE = 512;

	public synchronized void add(ChunkCompileTask task) {
		if (queue.size() < MAX_QUEUE_SIZE) {
			queue.offer(task);
		}
	}

	public synchronized ChunkCompileTask take() throws InterruptedException {
		while (queue.isEmpty()) {
			wait();
		}
		return queue.poll();
	}

	public synchronized ChunkCompileTask poll() {
		return queue.poll();
	}

	public synchronized int size() {
		return queue.size();
	}

	public synchronized void clear() {
		queue.clear();
	}
}
