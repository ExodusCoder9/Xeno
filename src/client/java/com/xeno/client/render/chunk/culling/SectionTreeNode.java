package com.xeno.client.render.chunk.culling;

public interface SectionTreeNode {
	void visit(Frustum frustum, java.util.List<?> visibleOut, java.util.List<?> nearbyOut);
}
