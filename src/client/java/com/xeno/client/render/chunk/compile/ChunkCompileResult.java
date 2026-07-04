package com.xeno.client.render.chunk.compile;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import java.nio.ByteBuffer;

public record ChunkCompileResult(
	long sectionKey,
	LayerResult[] layers,
	boolean success
) {
	public record LayerResult(
		ChunkSectionLayer layer,
		ByteBuffer vertexData,
		ByteBuffer indexData,
		int vertexCount,
		int indexCount
	) {}
}
