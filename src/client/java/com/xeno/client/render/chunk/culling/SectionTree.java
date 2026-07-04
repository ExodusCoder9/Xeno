package com.xeno.client.render.chunk.culling;

import com.xeno.client.render.chunk.storage.XenoSection;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class SectionTree {
	private BranchNode root;

	public void rebuild(Long2ObjectOpenHashMap<XenoSection> sectionMap) {
		root = new BranchNode();
		for (var entry : sectionMap.long2ObjectEntrySet()) {
			XenoSection section = entry.getValue();
			root.addLeaf(section);
		}
	}

	public void traverse(Frustum frustum, ObjectArrayList<XenoSection> visibleOut, ObjectArrayList<XenoSection> nearbyOut) {
		if (root != null) {
			root.visit(frustum, visibleOut, nearbyOut);
		}
	}

	private static class BranchNode {
		private final ObjectArrayList<Object> children = new ObjectArrayList<>();

		void addLeaf(XenoSection section) {
			children.add(section);
		}

		void visit(Frustum frustum, ObjectArrayList<XenoSection> visibleOut, ObjectArrayList<XenoSection> nearbyOut) {
			for (Object child : children) {
				if (child instanceof XenoSection section) {
					if (frustum.testSection(section.getX(), section.getY(), section.getZ())) {
						visibleOut.add(section);
					}
					if (frustum.testSectionExpanded(section.getX(), section.getY(), section.getZ(), 2)) {
						nearbyOut.add(section);
					}
				}
			}
		}
	}
}
