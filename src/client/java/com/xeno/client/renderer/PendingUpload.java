package com.xeno.client.renderer;

import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import java.nio.ByteBuffer;

public record PendingUpload(
    CompiledSectionMesh mesh,
    ChunkSectionLayer layer,
    ByteBuffer vertexData,
    ByteBuffer indexData,
    Runnable callback
) {}
