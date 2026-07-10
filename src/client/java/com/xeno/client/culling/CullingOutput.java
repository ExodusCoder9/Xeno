package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.List;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;

public record CullingOutput(
        List<SectionRenderDispatcher.RenderSection> occlusionVisible,
        Vec3 cameraPos,
        Long2ByteOpenHashMap sectionVisibility,
        Long2BooleanOpenHashMap opaqueSections,
        float cameraYaw,
        float cameraPitch
) {}
