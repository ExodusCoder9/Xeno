package com.xeno.client.render.chunk;

import com.xeno.client.render.chunk.compile.ChunkCompileTask;

public class ChunkBuildPriority implements Comparable<ChunkBuildPriority> {
	public enum UpdateType {
		INITIAL,
		REBUILD_NEAR,
		REBUILD,
		REBUILD_FAR,
		RESORT_ONLY
	}

	private final long sectionKey;
	private final UpdateType type;
	private final float distanceFromCamera;
	private final long submitTime;

	public ChunkBuildPriority(long sectionKey, UpdateType type, float distance) {
		this.sectionKey = sectionKey;
		this.type = type;
		this.distanceFromCamera = distance;
		this.submitTime = System.nanoTime();
	}

	@Override
	public int compareTo(ChunkBuildPriority other) {
		int typeCompare = this.type.compareTo(other.type);
		if (typeCompare != 0) return typeCompare;
		return Float.compare(this.distanceFromCamera, other.distanceFromCamera);
	}
}
