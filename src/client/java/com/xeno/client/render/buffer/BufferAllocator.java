package com.xeno.client.render.buffer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class BufferAllocator {
	private final Long2ObjectOpenHashMap<Object> regionVertexAllocators = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<Object> regionIndexAllocators = new Long2ObjectOpenHashMap<>();
	private static final int REGION_VERTEX_HEAP_SIZE = 4 * 1024 * 1024;
	private static final int REGION_INDEX_HEAP_SIZE = 1 * 1024 * 1024;

	public void free(long regionKey, int vertexAllocHandle, int indexAllocHandle) {}

	public void resetRegion(long regionKey) {
		regionVertexAllocators.remove(regionKey);
		regionIndexAllocators.remove(regionKey);
	}
}
