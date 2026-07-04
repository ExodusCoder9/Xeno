package com.xeno.client.render.light;

public class LightDataCache {
	private final int[] lightData = new int[18 * 18 * 18];
	private final boolean[] opacityData = new boolean[18 * 18 * 18];

	public void init(Object region, int originX, int originY, int originZ) {
	}

	public int getLight(int x, int y, int z) {
		return lightData[index(x + 1, y + 1, z + 1)];
	}

	public boolean isOpaque(int x, int y, int z) {
		return opacityData[index(x + 1, y + 1, z + 1)];
	}

	private static int index(int x, int y, int z) {
		return (y * 18 * 18) + (z * 18) + x;
	}
}
