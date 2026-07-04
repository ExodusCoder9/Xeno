package com.xeno.client.render.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;

public class SectionStorage {
	private final Long2ObjectOpenHashMap<XenoSection> sections = new Long2ObjectOpenHashMap<>();

	public XenoSection getOrCreateSection(long sectionKey, RenderSection vanilla) {
		XenoSection section = sections.get(sectionKey);
		if (section == null) {
			section = new XenoSection(vanilla);
			sections.put(sectionKey, section);
		}
		return section;
	}

	public XenoSection getSection(long sectionKey) {
		return sections.get(sectionKey);
	}

	public void removeSection(long sectionKey) {
		sections.remove(sectionKey);
	}

	public void clear() {
		sections.clear();
	}

	public Collection<XenoSection> getAllSections() {
		return sections.values();
	}

	public Collection<XenoSection> getVisibleSections() {
		return sections.values().stream().filter(XenoSection::isVisible).toList();
	}

	public Collection<XenoSection> getNearbySections() {
		return sections.values().stream().filter(XenoSection::isNearby).toList();
	}

	public Long2ObjectOpenHashMap<XenoSection> getSnapshot() {
		return sections.clone();
	}
}
