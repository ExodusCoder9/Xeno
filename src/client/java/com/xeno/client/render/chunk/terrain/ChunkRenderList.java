package com.xeno.client.render.chunk.terrain;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.buffers.GpuBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import java.util.Collection;

public class ChunkRenderList {
	private final Long2ObjectOpenHashMap<RenderRegion> regions = new Long2ObjectOpenHashMap<>();

	public void clear() { regions.clear(); }

	public void addSection(long regionKey, RenderRegion region) {
		regions.putIfAbsent(regionKey, region);
	}

	public void build() {}

	public void bindResources(RenderPass pass) {}

	public Collection<RenderRegion> getRegions() { return regions.values(); }
	public RenderRegion getRegion(long regionKey) { return regions.get(regionKey); }
	public GpuBufferSlice getSharedVertexSlice() { return null; }
	public GpuBuffer getSharedIndexBuffer() { return null; }
	public GpuBufferSlice getIndirectCommandsBufferSlice(ChunkSectionLayer layer) { return null; }
	public int getVisibleDrawCount(ChunkSectionLayer layer) { return 0; }
}
