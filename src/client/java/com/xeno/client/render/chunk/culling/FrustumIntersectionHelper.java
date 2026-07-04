package com.xeno.client.render.chunk.culling;

public class FrustumIntersectionHelper {
	// SIMD-optimized plane tests placeholder
	public static boolean testPoint(float[] planes, int planeIdx, float x, float y, float z) {
		int i = planeIdx * 4;
		return x * planes[i] + y * planes[i+1] + z * planes[i+2] + planes[i+3] >= 0;
	}
}
