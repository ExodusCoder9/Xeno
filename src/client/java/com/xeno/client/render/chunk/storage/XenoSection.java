package com.xeno.client.render.chunk.storage;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.SectionPos;

public class XenoSection {
	private final RenderSection vanillaSection;
	private final long sectionKey;
	private final int x, y, z;
	private SectionState state = SectionState.UNLOADED;
	private boolean visible;
	private boolean nearby;

	private final TlsfAllocator.Allocation[] vertexAllocations = new TlsfAllocator.Allocation[3];
	private final TlsfAllocator.Allocation[] indexAllocations = new TlsfAllocator.Allocation[3];
	private final int[] vertexCounts = new int[3];
	private final int[] indexCounts = new int[3];

	private int packedLight;
	private int flags;
	private int framesSinceCompiled;
	private long lastModifiedTick;
	private boolean wasRecentlyModified;

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

	public TlsfAllocator.Allocation getVertexAllocation(ChunkSectionLayer layer) {
		return vertexAllocations[layer.ordinal()];
	}

	public void setVertexAllocation(ChunkSectionLayer layer, TlsfAllocator.Allocation alloc) {
		this.vertexAllocations[layer.ordinal()] = alloc;
	}

	public TlsfAllocator.Allocation getIndexAllocation(ChunkSectionLayer layer) {
		return indexAllocations[layer.ordinal()];
	}

	public void setIndexAllocation(ChunkSectionLayer layer, TlsfAllocator.Allocation alloc) {
		this.indexAllocations[layer.ordinal()] = alloc;
	}

	public int getVertexCount(ChunkSectionLayer layer) {
		return vertexCounts[layer.ordinal()];
	}

	public void setVertexCount(ChunkSectionLayer layer, int count) {
		this.vertexCounts[layer.ordinal()] = count;
	}

	public int getIndexCount(ChunkSectionLayer layer) {
		return indexCounts[layer.ordinal()];
	}

	public void setIndexCount(ChunkSectionLayer layer, int count) {
		this.indexCounts[layer.ordinal()] = count;
	}

	public int getPackedLight() { return packedLight; }
	public void setPackedLight(int l) { this.packedLight = l; }
	public int getFlags() { return flags; }
	public void setFlags(int f) { this.flags = f; }
	public int getFramesSinceCompiled() { return framesSinceCompiled; }
	public void setFramesSinceCompiled(int f) { this.framesSinceCompiled = f; }
	public void markModified() { lastModifiedTick = System.nanoTime(); wasRecentlyModified = true; }
	public boolean wasRecentlyModified() { return wasRecentlyModified; }
	public void clearRecentlyModified() { wasRecentlyModified = false; }
}
