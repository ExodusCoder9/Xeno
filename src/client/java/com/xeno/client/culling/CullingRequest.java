package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record CullingRequest(
    BlockPos cameraBlockPos,
    Vec3 cameraPos,
    boolean smartCull,
    Frustum frustum,
    int fov,
    ViewArea viewArea,
    SectionRenderDispatcher.RenderSection[] sectionArray,
    int minY,
    int maxY,
    int sizeY,
    int sizeXZ,
    int viewDistance,
    LongOpenHashSet emptySections,
    LongOpenHashSet loadedChunks,
    List<SectionRenderDispatcher.RenderSection> propagations
) {}
