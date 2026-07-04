package com.xeno.client.render.chunk.info;

import com.mojang.blaze3d.systems.GpuDevice;

public class SectionInfoUploader {
	private final SectionInfoBuffer buffer;

	public SectionInfoUploader(SectionInfoBuffer buffer) {
		this.buffer = buffer;
	}

	public void upload(GpuDevice device) {
		buffer.uploadDirty(device);
	}
}
