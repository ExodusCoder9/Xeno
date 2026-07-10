package com.xeno.client.culling;

import java.util.List;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;

public record CullingOutput(
        List<SectionRenderDispatcher.RenderSection> occlusionVisible,
        Vec3 cameraPos
) {}
