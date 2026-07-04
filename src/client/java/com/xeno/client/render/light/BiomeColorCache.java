package com.xeno.client.render.light;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public class BiomeColorCache {
	private final Long2IntOpenHashMap colorCache = new Long2IntOpenHashMap();

	public enum ColorType {
		GRASS, FOLIAGE, WATER, REDSTONE_WIRE, STEM_AGE
	}

	public int getColor(int x, int y, int z, ColorType type) { return 0xFFFFFFFF; }
	public void invalidate(int x, int y, int z) {}
	public void clear() { colorCache.clear(); }
}
