package com.xeno.client.render.chunk.sorting;

public class SortKeyBuilder {
	public static long computeSortKey(float distance, int sectionIndex, int drawIndex) {
		int distBits = Float.floatToRawIntBits(distance);
		return ((long) distBits << 32) | ((long) sectionIndex << 16) | drawIndex;
	}
}
