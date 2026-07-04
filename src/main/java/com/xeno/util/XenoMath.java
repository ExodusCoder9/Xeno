package com.xeno.util;

public class XenoMath {
	public static final float POSITION_SCALE = 2048.0f;
	public static final float UV_SCALE = 65535.0f;

	public static int packLightAO(int blockLight, int skyLight, int ao, int normalIndex, int flags) {
		return (blockLight & 0xF)
			| ((skyLight & 0xF) << 4)
			| ((ao & 0x3) << 8)
			| ((normalIndex & 0x7) << 10)
			| ((flags & 0x7) << 13);
	}

	public static long sectionKey(int x, int y, int z) {
		return (((long) x & 0x3FFFF) << 38)
			| (((long) z & 0x3FFFF) << 12)
			| ((long) y & 0xFFF);
	}

	public static int sectionX(long key) { return (int) (key >> 38); }
	public static int sectionY(long key) { return (int) (key << 52 >> 52); }
	public static int sectionZ(long key) { return (int) ((key << 26) >> 38); }

	public static float fastInvSqrt(float x) {
		return 1.0f / (float) Math.sqrt(x);
	}
}
