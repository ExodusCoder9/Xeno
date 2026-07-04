package com.xeno.client.render.chunk.culling;

import com.xeno.client.render.chunk.storage.XenoSection;
import java.util.List;

public class CullingOutput {
	private final List<XenoSection> visibleSections;
	private final List<XenoSection> nearbySections;
	private final Object entityVisibilityMap;
	private final long frameIndex;
	private final double cullTimeMs;

	public CullingOutput(
		List<XenoSection> visibleSections,
		List<XenoSection> nearbySections,
		Object entityVisibilityMap,
		long frameIndex,
		double cullTimeMs
	) {
		this.visibleSections = visibleSections;
		this.nearbySections = nearbySections;
		this.entityVisibilityMap = entityVisibilityMap;
		this.frameIndex = frameIndex;
		this.cullTimeMs = cullTimeMs;
	}

	public List<XenoSection> getVisibleSections() { return visibleSections; }
	public List<XenoSection> getNearbySections() { return nearbySections; }
	public Object getEntityVisibilityMap() { return entityVisibilityMap; }
	public long getFrameIndex() { return frameIndex; }
	public double getCullTimeMs() { return cullTimeMs; }
}
