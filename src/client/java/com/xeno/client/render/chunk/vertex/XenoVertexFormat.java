package com.xeno.client.render.chunk.vertex;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public class XenoVertexFormat {
	public static final int VERTEX_SIZE = 16;

	public static final VertexFormat FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB16_SNORM)
		.addAttribute("UV", GpuFormat.RG16_UNORM)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.addAttribute("LightAO", GpuFormat.R16_UNORM)
		.build();

	public static VertexFormat getFormat() { return FORMAT; }
	public static int getVertexSize() { return VERTEX_SIZE; }
}
