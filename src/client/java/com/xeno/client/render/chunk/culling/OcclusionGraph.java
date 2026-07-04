package com.xeno.client.render.chunk.culling;

import com.xeno.client.render.chunk.storage.XenoSection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;

public class OcclusionGraph {
	private static final int FULL_UPDATE_THRESHOLD = 8;
	private Vec3 lastCameraPos = new Vec3(0, 0, 0);
	private float lastFov;
	private boolean needsFullUpdate = true;

	public static class Vec3 {
		public double x, y, z;
		public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
	}

	public void update(
		Long2ObjectOpenHashMap<XenoSection> sectionMap,
		Vec3 cameraPos, float fov
	) {
		double dx = cameraPos.x - lastCameraPos.x;
		double dy = cameraPos.y - lastCameraPos.y;
		double dz = cameraPos.z - lastCameraPos.z;
		double distSq = dx*dx + dy*dy + dz*dz;

		if (distSq > FULL_UPDATE_THRESHOLD * FULL_UPDATE_THRESHOLD || fov != lastFov) {
			needsFullUpdate = true;
		}
		lastCameraPos = cameraPos;
		lastFov = fov;

		if (needsFullUpdate) {
			runFullUpdate(sectionMap, cameraPos);
			needsFullUpdate = false;
		} else {
			runPartialUpdate(sectionMap, cameraPos);
		}
	}

	private void runFullUpdate(Long2ObjectOpenHashMap<XenoSection> sectionMap, Vec3 cameraPos) {
		for (var entry : sectionMap.long2ObjectEntrySet()) {
			XenoSection section = entry.getValue();
			section.setVisible(true);
		}
	}

	private void runPartialUpdate(Long2ObjectOpenHashMap<XenoSection> sectionMap, Vec3 cameraPos) {
	}

	public void invalidateIfNeeded(Vec3 cameraPos, float fov) {
		double dx = cameraPos.x - lastCameraPos.x;
		double dy = cameraPos.y - lastCameraPos.y;
		double dz = cameraPos.z - lastCameraPos.z;
		if (dx*dx + dy*dy + dz*dz > FULL_UPDATE_THRESHOLD * FULL_UPDATE_THRESHOLD) {
			needsFullUpdate = true;
		}
	}

	public boolean needsFullUpdate() { return needsFullUpdate; }
}
