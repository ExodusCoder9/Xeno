package com.xeno.client.render.chunk.compile;

import com.xeno.client.render.chunk.storage.XenoSection;
import net.minecraft.core.SectionPos;

public class ChunkCompileTask implements Comparable<ChunkCompileTask> {
	public enum UpdateType {
		INITIAL,
		REBUILD_NEAR,
		REBUILD,
		REBUILD_FAR,
		RESORT_ONLY
	}

	private final XenoSection section;
	private final UpdateType type;
	private final float distanceFromCamera;
	private final long submitTime;

	public ChunkCompileTask(XenoSection section, UpdateType type, float distanceFromCamera) {
		this.section = section;
		this.type = type;
		this.distanceFromCamera = distanceFromCamera;
		this.submitTime = System.nanoTime();
	}

	public XenoSection getSection() { return section; }
	public UpdateType getType() { return type; }
	public float getDistanceFromCamera() { return distanceFromCamera; }
	public long getSubmitTime() { return submitTime; }

	@Override
	public int compareTo(ChunkCompileTask other) {
		int typeCompare = this.type.compareTo(other.type);
		if (typeCompare != 0) return typeCompare;
		return Float.compare(this.distanceFromCamera, other.distanceFromCamera);
	}
}
