package com.xeno.client.render.chunk.sorting;

import com.xeno.client.render.chunk.storage.XenoSection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public class TranslucentSorter {
	public static ObjectArrayList<XenoSection> sortSections(
		ObjectArrayList<XenoSection> sections,
		Vec3 cameraPos
	) {
		ObjectArrayList<XenoSection> sorted = new ObjectArrayList<>(sections);
		sorted.sort((a, b) -> {
			double da = distanceSq(a.getVanillaSection().getBoundingBox(), cameraPos);
			double db = distanceSq(b.getVanillaSection().getBoundingBox(), cameraPos);
			return Double.compare(db, da);
		});
		return sorted;
	}

	private static double distanceSq(AABB bb, Vec3 pos) {
		double cx = (bb.minX + bb.maxX) * 0.5;
		double cy = (bb.minY + bb.maxY) * 0.5;
		double cz = (bb.minZ + bb.maxZ) * 0.5;
		double dx = cx - pos.x;
		double dy = cy - pos.y;
		double dz = cz - pos.z;
		return dx * dx + dy * dy + dz * dz;
	}
}
