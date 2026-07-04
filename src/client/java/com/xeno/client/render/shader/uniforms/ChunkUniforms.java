package com.xeno.client.render.shader.uniforms;

import com.mojang.blaze3d.systems.RenderPass;

public class ChunkUniforms {
	private static final int VEC4_SIZE = 16;
	private static final int MAX_REGIONS = 256;

	public void setRegionOffset(int regionIndex, float x, float y, float z) {}
	public void bind(RenderPass pass) {}
}
