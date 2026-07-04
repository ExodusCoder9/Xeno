package com.xeno.client.render.buffer;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.nio.ByteBuffer;

public class StagingBuffer {
	private static final int BUFFER_COUNT = 3;
	private final StagingSlot[] slots;
	private int writeSlot;
	private final Long2IntOpenHashMap regionOffsets = new Long2IntOpenHashMap();

	public StagingBuffer(int slotSize) {
		this.slots = new StagingSlot[BUFFER_COUNT];
		GpuDevice device = RenderSystem.getDevice();
		for (int i = 0; i < BUFFER_COUNT; i++) {
			final int slotIndex = i;
			this.slots[i] = new StagingSlot(
				device.createBuffer(
					() -> "Xeno-Staging-" + slotIndex,
					GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_HINT_CLIENT_STORAGE,
					slotSize
				)
			);
		}
	}

	public void beginFrame() {
		writeSlot = (writeSlot + 1) % BUFFER_COUNT;
		regionOffsets.clear();
		unmap();
	}

	public long reserve(long regionKey, int size) {
		int offset = regionOffsets.get(regionKey);
		regionOffsets.put(regionKey, offset + size);
		return offset;
	}

	public ByteBuffer map(long offset, long size) {
		if (slots[writeSlot].mappedView != null) {
			throw new IllegalStateException("Already mapped");
		}
		slots[writeSlot].mappedView = slots[writeSlot].buffer.slice(offset, size).map(false, true);
		return slots[writeSlot].mappedView.data();
	}

	public void unmap() {
		if (slots[writeSlot].mappedView != null) {
			slots[writeSlot].mappedView.close();
			slots[writeSlot].mappedView = null;
		}
	}

	public void submitUpload(CommandEncoder encoder, GpuBuffer destination, long srcOffset, long destOffset, long size) {
		unmap();
		encoder.copyToBuffer(
			slots[writeSlot].buffer.slice(srcOffset, size),
			destination.slice(destOffset, size)
		);
	}

	private static class StagingSlot {
		final GpuBuffer buffer;
		GpuBufferSlice.MappedView mappedView;
		StagingSlot(GpuBuffer buffer) { this.buffer = buffer; }
	}
}
