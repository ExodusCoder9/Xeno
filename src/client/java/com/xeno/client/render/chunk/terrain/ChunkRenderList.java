package com.xeno.client.render.chunk.terrain;

import com.xeno.client.render.chunk.storage.XenoSection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public class ChunkRenderList {
	private final Long2ObjectOpenHashMap<RegionDrawList> regionDrawLists = new Long2ObjectOpenHashMap<>();

	public void clear() { regionDrawLists.clear(); }

	public void addSection(long regionKey, RenderRegion region, XenoSection section) {
		regionDrawLists.computeIfAbsent(regionKey, k -> new RegionDrawList(region)).visibleSections.add(section);
	}

	public Collection<RegionDrawList> getRegionDrawLists() { return regionDrawLists.values(); }

	public static class RegionDrawList {
		private final RenderRegion region;
		private final List<XenoSection> visibleSections = new ArrayList<>();

		public RegionDrawList(RenderRegion region) {
			this.region = region;
		}

		public RenderRegion getRegion() { return region; }
		public List<XenoSection> getVisibleSections() { return visibleSections; }
	}
}
