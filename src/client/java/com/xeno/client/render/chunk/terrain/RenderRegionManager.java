package com.xeno.client.render.chunk.terrain;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class RenderRegionManager {
	private final Long2ObjectOpenHashMap<RenderRegion> regions = new Long2ObjectOpenHashMap<>();

	public RenderRegion getOrCreateRegion(int rx, int ry, int rz) {
		long key = regionKey(rx, ry, rz);
		return regions.computeIfAbsent(key, k -> new RenderRegion(rx, ry, rz, k));
	}

	public void removeRegion(long key) { regions.remove(key); }
	public void clear() { regions.clear(); }

	public static long regionKey(int rx, int ry, int rz) {
		return (((long) rx & 0x3FFFF) << 38)
			| (((long) rz & 0x3FFFF) << 12)
			| ((long) ry & 0xFFF);
	}
}
