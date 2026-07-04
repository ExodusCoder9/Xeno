package com.xeno.client.render.chunk.vertex;

import java.nio.ByteBuffer;

public class VertexPacker {
	public static final float POSITION_SCALE = 2048.0f;
	public static final float UV_SCALE = 65535.0f;

	public static void writeVertex(
		ByteBuffer buffer,
		float x, float y, float z,
		float u, float v,
		int r, int g, int b, int a,
		int blockLight, int skyLight,
		int ao, int normalIndex, int flags
	) {
		buffer.putShort((short) (x * POSITION_SCALE));
		buffer.putShort((short) (y * POSITION_SCALE));
		buffer.putShort((short) (z * POSITION_SCALE));
		buffer.putShort((short) (u * UV_SCALE));
		buffer.putShort((short) (v * UV_SCALE));
		buffer.put((byte) r);
		buffer.put((byte) g);
		buffer.put((byte) b);
		buffer.put((byte) a);
		int packed = (blockLight & 0xF)
			| ((skyLight & 0xF) << 4)
			| ((ao & 0x3) << 8)
			| ((normalIndex & 0x7) << 10)
			| ((flags & 0x7) << 13);
		buffer.putShort((short) packed);
	}
}
