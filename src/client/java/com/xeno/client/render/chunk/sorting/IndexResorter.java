package com.xeno.client.render.chunk.sorting;

import net.minecraft.world.phys.Vec3;

public class IndexResorter {
	private static final double RESORT_THRESHOLD_SQ = 4.0;

	public static boolean needsResort(Object mesh, Vec3 cameraPos, int sectionX, int sectionY, int sectionZ) {
		return false;
	}

	public static void resortIndices(Object mesh, Object pack, Object layer, Vec3 cameraPos,
									 int sectionX, int sectionY, int sectionZ) {
	}
}
