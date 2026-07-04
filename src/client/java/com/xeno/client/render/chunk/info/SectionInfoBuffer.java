package com.xeno.client.render.chunk.info;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.BitSet;

public class SectionInfoBuffer {
	private final GpuBuffer buffer;
	private final int maxSections;
	private final ByteBuffer cpuBuffer;
	private final BitSet dirtySections;

	public SectionInfoBuffer(GpuDevice device, int maxSections) {
		this.maxSections = maxSections;
		this.cpuBuffer = ByteBuffer.allocate(maxSections * 32);
		this.dirtySections = new BitSet(maxSections);
		this.buffer = null; // Will be created when device is available
	}

	public void setSectionInfo(int sectionIndex, SectionInfoEntry entry) {
		int offset = sectionIndex * 32;
		cpuBuffer.putInt(offset, entry.vertexOffset());
		cpuBuffer.putInt(offset + 4, entry.indexOffset());
		cpuBuffer.putInt(offset + 8, entry.vertexCount());
		cpuBuffer.putInt(offset + 12, entry.indexCount());
		cpuBuffer.putInt(offset + 16, entry.packedLight());
		cpuBuffer.putInt(offset + 20, entry.flags());
		markDirty(sectionIndex);
	}

	public void markDirty(int sectionIndex) { dirtySections.set(sectionIndex); }

	public void uploadDirty(GpuDevice device) {}

	public GpuBufferSlice getBufferSlice() { return null; }
}
