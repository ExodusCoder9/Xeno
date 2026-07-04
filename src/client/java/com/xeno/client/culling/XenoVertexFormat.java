package com.xeno.client.culling;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public class XenoVertexFormat {
    public static final VertexFormat XENO_COMPRESSED_FORMAT = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGBA16_SINT)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV0", GpuFormat.RG16_SINT)
        .addAttribute("UV2", GpuFormat.RG16_SINT)
        .build();
}
