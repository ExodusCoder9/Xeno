package com.xeno.client.render.chunk.info;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
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
		this.buffer = device.createBuffer(() -> "SectionInfo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long)maxSections * 32L);
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

	public void markDirty(int sectionIndex) {
		dirtySections.set(sectionIndex);
	}

	public void uploadDirty(GpuDevice device) {
		if (dirtySections.isEmpty()) return;
		CommandEncoder encoder = device.createCommandEncoder();
		for (int i = dirtySections.nextSetBit(0); i >= 0; i = dirtySections.nextSetBit(i + 1)) {
			int offset = i * 32;
			ByteBuffer sectionData = ByteBuffer.allocate(32);
			sectionData.putInt(cpuBuffer.getInt(offset));
			sectionData.putInt(cpuBuffer.getInt(offset + 4));
			sectionData.putInt(cpuBuffer.getInt(offset + 8));
			sectionData.putInt(cpuBuffer.getInt(offset + 12));
			sectionData.putInt(cpuBuffer.getInt(offset + 16));
			sectionData.putInt(cpuBuffer.getInt(offset + 20));
			sectionData.rewind();
			encoder.writeToBuffer(buffer.slice((long)i * 32L, 32L), sectionData);
		}
		encoder.submit();
		dirtySections.clear();
	}

	public GpuBufferSlice getBufferSlice() {
		return buffer.slice();
	}
}
