package com.xeno.client.render.buffer;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class BufferAllocator {
	private final Long2ObjectOpenHashMap<TlsfAllocator> regionVertexAllocators = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<TlsfAllocator> regionIndexAllocators = new Long2ObjectOpenHashMap<>();
	private static final int REGION_VERTEX_HEAP_SIZE = 4 * 1024 * 1024;
	private static final int REGION_INDEX_HEAP_SIZE = 1 * 1024 * 1024;

	public TlsfAllocator.Allocation allocateVertex(long regionKey, long size) {
		TlsfAllocator allocator = regionVertexAllocators.computeIfAbsent(regionKey, k -> new TlsfAllocator(new TlsfAllocator.Heap(REGION_VERTEX_HEAP_SIZE)));
		return allocator.allocate(size, 32);
	}

	public TlsfAllocator.Allocation allocateIndex(long regionKey, long size) {
		TlsfAllocator allocator = regionIndexAllocators.computeIfAbsent(regionKey, k -> new TlsfAllocator(new TlsfAllocator.Heap(REGION_INDEX_HEAP_SIZE)));
		return allocator.allocate(size, 32);
	}

	public void freeVertex(long regionKey, TlsfAllocator.Allocation allocation) {
		TlsfAllocator allocator = regionVertexAllocators.get(regionKey);
		if (allocator != null && allocation != null) {
			allocator.free(allocation);
		}
	}

	public void freeIndex(long regionKey, TlsfAllocator.Allocation allocation) {
		TlsfAllocator allocator = regionIndexAllocators.get(regionKey);
		if (allocator != null && allocation != null) {
			allocator.free(allocation);
		}
	}

	public void resetRegion(long regionKey) {
		regionVertexAllocators.remove(regionKey);
		regionIndexAllocators.remove(regionKey);
	}
}
