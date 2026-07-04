package com.xeno.client.render.chunk.culling;

import com.xeno.client.render.chunk.storage.XenoSection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class EntityCuller {
	private final AtomicReference<Object> visibilityMap = new AtomicReference<>();

	public void update(
		OcclusionGraph.Vec3 cameraPos,
		Frustum frustum,
		Long2ObjectOpenHashMap<XenoSection> sectionMap
	) {
		// Placeholder: mark all entities in visible sections as visible
	}

	public Object getVisibilityMap() {
		return visibilityMap.getAndSet(null);
	}
}
