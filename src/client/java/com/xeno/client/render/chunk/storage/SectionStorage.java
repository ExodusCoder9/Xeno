package com.xeno.client.render.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class SectionStorage {
	private final Long2ObjectOpenHashMap<XenoSection> sections = new Long2ObjectOpenHashMap<>();

	public XenoSection get(long sectionKey) {
		return sections.get(sectionKey);
	}

	public void put(long sectionKey, XenoSection section) {
		sections.put(sectionKey, section);
	}

	public void remove(long sectionKey) {
		sections.remove(sectionKey);
	}

	public boolean contains(long sectionKey) {
		return sections.containsKey(sectionKey);
	}

	public Long2ObjectOpenHashMap<XenoSection> getSnapshot() {
		return sections.clone();
	}

	public void clear() {
		sections.clear();
	}

	public int size() {
		return sections.size();
	}

	public Long2ObjectOpenHashMap.FastEntrySet<XenoSection> entrySet() {
		return sections.long2ObjectEntrySet();
	}
}
