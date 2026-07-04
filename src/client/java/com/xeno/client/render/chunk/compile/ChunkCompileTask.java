package com.xeno.client.render.chunk.compile;

import net.minecraft.core.SectionPos;

public record ChunkCompileTask(long sectionKey, SectionPos sectionPos, int priority) implements Comparable<ChunkCompileTask> {
	@Override
	public int compareTo(ChunkCompileTask other) {
		return Integer.compare(this.priority, other.priority);
	}
}
