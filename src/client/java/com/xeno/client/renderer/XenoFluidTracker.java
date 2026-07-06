package com.xeno.client.renderer;

import com.mojang.blaze3d.vertex.MeshData;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;

public class XenoFluidTracker {
    private static final ThreadLocal<Map<SectionMesh, Map<ChunkSectionLayer, MeshData>>> FLUID_MESHES =
        ThreadLocal.withInitial(java.util.HashMap::new);

    public static Map<SectionMesh, Map<ChunkSectionLayer, MeshData>> get() {
        return FLUID_MESHES.get();
    }

    public static void set(SectionMesh mesh, Map<ChunkSectionLayer, MeshData> fluidLayers) {
        get().put(mesh, fluidLayers);
    }

    public static Map<ChunkSectionLayer, MeshData> remove(SectionMesh mesh) {
        return get().remove(mesh);
    }
}
