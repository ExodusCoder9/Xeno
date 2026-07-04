package com.xeno.client.render.buffer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;

public class MemoryDefragmenter {
	private static final long DEFRAG_INTERVAL = 600;
	private static final float FRAGMENTATION_THRESHOLD = 0.3f;
	private long frameCounter;
	private final Long2ObjectOpenHashMap<RegionFragStats> regionStats = new Long2ObjectOpenHashMap<>();

	public List<Long> getRegionsToDefrag() {
		frameCounter++;
		if (frameCounter % DEFRAG_INTERVAL != 0) return List.of();
		return List.of();
	}

	public void defragment(long regionKey, UploadManager uploadManager) {
	}

	private record RegionFragStats(long totalAllocated, long totalFree, int freeChunks, int largestFreeChunk) {
		float fragmentation() {
			return 1.0f - ((float) largestFreeChunk / (float) totalFree);
		}
	}
}
