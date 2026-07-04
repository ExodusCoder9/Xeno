package com.xeno.client.render.chunk.compile;

import java.nio.ByteBuffer;

public record ChunkCompileResult(
	long sectionKey,
	ByteBuffer vertexData,
	ByteBuffer indexData,
	int vertexCount,
	int indexCount,
	int packedLight,
	int flags,
	boolean success
) {
}
