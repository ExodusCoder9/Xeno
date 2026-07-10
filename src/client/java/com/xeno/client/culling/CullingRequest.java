package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class CullingRequest {
    public BlockPos cameraBlockPos;
    public Vec3 cameraPos;
    public boolean smartCull;
    public Frustum frustum;
    public int fov;
    public ViewArea viewArea;
    public SectionRenderDispatcher.RenderSection[] sectionArray = new SectionRenderDispatcher.RenderSection[0];
    public int minY;
    public int maxY;
    public int sizeY;
    public int sizeXZ;
    public int viewDistance;
    public final LongOpenHashSet emptySections = new LongOpenHashSet();
    public final LongOpenHashSet loadedChunks = new LongOpenHashSet();
    public final List<SectionRenderDispatcher.RenderSection> propagations = new ArrayList<>();
}
