package com.xeno.client.render.chunk.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public class EntityVisibilityMap {
	private final LongOpenHashSet visibleEntities;
	private final LongOpenHashSet nearbyEntities;

	public EntityVisibilityMap() {
		this.visibleEntities = new LongOpenHashSet();
		this.nearbyEntities = new LongOpenHashSet();
	}

	public boolean isVisible(int entityId) { return visibleEntities.contains(entityId); }
	public boolean isNearby(int entityId) { return nearbyEntities.contains(entityId); }
	public void addVisible(int id) { visibleEntities.add(id); }
	public void addNearby(int id) { nearbyEntities.add(id); }
}
