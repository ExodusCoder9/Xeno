package com.xeno.client.render.chunk.terrain;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.buffers.GpuBuffer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public class RenderRegion {
	public static final int SIZE_SECTIONS = 8;

	private final int rx, ry, rz;
	private final long regionKey;

	public RenderRegion(int rx, int ry, int rz, long regionKey) {
		this.rx = rx;
		this.ry = ry;
		this.rz = rz;
		this.regionKey = regionKey;
	}

	public long getRegionKey() { return regionKey; }
	public int getRx() { return rx; }
	public int getRy() { return ry; }
	public int getRz() { return rz; }

	public GpuBufferSlice getVertexSlice() { return null; }
	public GpuBuffer getIndexBuffer() { return null; }
	public int getIndexCount(ChunkSectionLayer layer) { return 0; }
	public int getFirstIndex(ChunkSectionLayer layer) { return 0; }
	public int getBaseVertex(ChunkSectionLayer layer) { return 0; }
}
