package com.xeno.client.render.chunk.culling;

public class OcclusionNode {
	private final long sectionKey;
	private int visibleFaces;

	public OcclusionNode(long sectionKey) {
		this.sectionKey = sectionKey;
		this.visibleFaces = 0x3F;
	}

	public long getSectionKey() { return sectionKey; }
	public int getVisibleFaces() { return visibleFaces; }
	public void setVisibleFaces(int f) { visibleFaces = f; }
	public boolean hasFace(int faceMask) { return (visibleFaces & faceMask) != 0; }
}
