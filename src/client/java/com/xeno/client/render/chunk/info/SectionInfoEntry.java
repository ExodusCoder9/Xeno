package com.xeno.client.render.chunk.info;

public record SectionInfoEntry(
	int vertexOffset, int indexOffset,
	int vertexCount, int indexCount,
	int packedLight, int flags
) {
	public static final int FLAG_EMPTY = 1 << 0;
	public static final int FLAG_HAS_TRANSLUCENT = 1 << 1;
	public static final int FLAG_HAS_SOLID = 1 << 2;
	public static final int FLAG_HAS_CUTOUT = 1 << 3;
	public static final int FLAG_VISIBLE = 1 << 4;
}
