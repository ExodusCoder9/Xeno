package com.xeno.client.renderer.frapi;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * Fabric Rendering API (FRAPI) Interop Handler.
 * Allows custom modded block models (Create, TechReborn, AE2) to stream quad geometry
 * directly into Xeno's high-performance vertex buffers.
 */
public class XenoFrapiMesh {

    public static void emitQuad(BufferBuilder builder, BakedQuad quad, float x, float y, float z, QuadInstance instance) {
        if (builder == null || quad == null) return;
        builder.putBlockBakedQuad(x, y, z, quad, instance);
    }
}
