package com.xeno.client.render.chunk.storage;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.SectionPos;

public class XenoSection {
	private final RenderSection vanillaSection;
	private final long sectionKey;
	private final int x, y, z;
	private SectionState state = SectionState.UNLOADED;
	private boolean visible;
	private boolean nearby;
	private int vertexOffset;
	private int indexOffset;
	private int vertexCount;
	private int indexCount;
	private int packedLight;
	private int flags;
	private int framesSinceCompiled;
	private long lastModifiedTick;

	public XenoSection(RenderSection vanillaSection) {
		this.vanillaSection = vanillaSection;
		long node = vanillaSection.getSectionNode();
		this.x = SectionPos.x(node);
		this.y = SectionPos.y(node);
		this.z = SectionPos.z(node);
		this.sectionKey = node;
	}

	public RenderSection getVanillaSection() { return vanillaSection; }
	public long getSectionKey() { return sectionKey; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getZ() { return z; }
	public SectionState getState() { return state; }
	public void setState(SectionState state) { this.state = state; }
	public boolean isVisible() { return visible; }
	public void setVisible(boolean v) { this.visible = v; }
	public boolean isNearby() { return nearby; }
	public void setNearby(boolean n) { this.nearby = n; }

	public int getVertexOffset() { return vertexOffset; }
	public void setVertexOffset(int o) { this.vertexOffset = o; }
	public int getIndexOffset() { return indexOffset; }
	public void setIndexOffset(int o) { this.indexOffset = o; }
	public int getVertexCount() { return vertexCount; }
	public void setVertexCount(int c) { this.vertexCount = c; }
	public int getIndexCount() { return indexCount; }
	public void setIndexCount(int c) { this.indexCount = c; }
	public int getPackedLight() { return packedLight; }
	public void setPackedLight(int l) { this.packedLight = l; }
	public int getFlags() { return flags; }
	public void setFlags(int f) { this.flags = f; }
	public int getFramesSinceCompiled() { return framesSinceCompiled; }
	public void setFramesSinceCompiled(int f) { this.framesSinceCompiled = f; }
	public void markModified() { lastModifiedTick = System.nanoTime(); wasRecentlyModified = true; }
	public boolean wasRecentlyModified() { return wasRecentlyModified; }
	public void clearRecentlyModified() { wasRecentlyModified = false; }
	private boolean wasRecentlyModified;
}
