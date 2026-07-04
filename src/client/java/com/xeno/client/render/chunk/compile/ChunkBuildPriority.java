package com.xeno.client.render.chunk.compile;

import java.util.Comparator;
import java.util.PriorityQueue;
import net.minecraft.core.SectionPos;

public class ChunkBuildPriority {
	private final PriorityQueue<ChunkCompileTask> queue;
	private double cameraX;
	private double cameraY;
	private double cameraZ;

	public ChunkBuildPriority() {
		this.queue = new PriorityQueue<>(Comparator.comparingDouble(this::computeDistance));
	}

	public void setCameraPosition(double x, double y, double z) {
		this.cameraX = x;
		this.cameraY = y;
		this.cameraZ = z;
	}

	private double computeDistance(ChunkCompileTask task) {
		SectionPos pos = task.sectionPos();
		double dx = pos.minBlockX() + 8.0 - cameraX;
		double dy = pos.minBlockY() + 8.0 - cameraY;
		double dz = pos.minBlockZ() + 8.0 - cameraZ;
		return dx * dx + dy * dy + dz * dz;
	}

	public void offer(ChunkCompileTask task) {
		queue.offer(task);
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
