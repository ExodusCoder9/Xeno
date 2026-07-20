package com.xeno.client.mixin;

import com.xeno.client.renderer.memory.XGenerationalMultiBufferAllocator;
import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.util.XenoMeshExtension;
import com.xeno.client.renderer.draw.XenoUniformBinder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.Map;

@Mixin(CompiledSectionMesh.class)
public abstract class CompiledSectionMeshMixin implements XenoMeshExtension {

    @Unique
    private final Map<ChunkSectionLayer, XGenerationalMultiBufferAllocator.AllocationHandle> xenoVertexAllocations = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, XGenerationalMultiBufferAllocator.AllocationHandle> xenoIndexAllocations = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, RenderPass.Draw<GpuBufferSlice[]>> xenoCachedDraws = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, XenoUniformBinder> xenoUniformBinders = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private float[] xenoTranslucentQuadCenters;

    @Unique
    private int xenoTranslucentQuadCount;

    @Override
    public void xeno$setAllocations(ChunkSectionLayer layer, XGenerationalMultiBufferAllocator.AllocationHandle vertexAlloc, XGenerationalMultiBufferAllocator.AllocationHandle indexAlloc) {
        this.xenoVertexAllocations.put(layer, vertexAlloc);
        if (indexAlloc != null) {
            this.xenoIndexAllocations.put(layer, indexAlloc);
        }
    }

    @Override
    public XGenerationalMultiBufferAllocator.AllocationHandle xeno$getVertexAllocation(ChunkSectionLayer layer) {
        return this.xenoVertexAllocations.get(layer);
    }

    @Override
    public XGenerationalMultiBufferAllocator.AllocationHandle xeno$getIndexAllocation(ChunkSectionLayer layer) {
        return this.xenoIndexAllocations.get(layer);
    }

    @Override
    public void xeno$clearBuffers() {
        XenoWorldRenderer.freeAllocations(this.xenoVertexAllocations, this.xenoIndexAllocations);
        this.xenoCachedDraws.clear();
        this.xenoUniformBinders.clear();
    }

    @Override
    public void xeno$setTranslucentData(float[] quadCenters, int quadCount) {
        this.xenoTranslucentQuadCenters = quadCenters;
        this.xenoTranslucentQuadCount = quadCount;
    }

    @Override
    public float[] xeno$getTranslucentQuadCenters() {
        return this.xenoTranslucentQuadCenters;
    }

    @Override
    public int xeno$getTranslucentQuadCount() {
        return this.xenoTranslucentQuadCount;
    }

    @Override
    public void xeno$setCachedDraw(ChunkSectionLayer layer, RenderPass.Draw<GpuBufferSlice[]> draw) {
        this.xenoCachedDraws.put(layer, draw);
    }

    @Override
    public RenderPass.Draw<GpuBufferSlice[]> xeno$getCachedDraw(ChunkSectionLayer layer) {
        return this.xenoCachedDraws.get(layer);
    }

    @Override
    public void xeno$setUniformBinder(ChunkSectionLayer layer, XenoUniformBinder binder) {
        this.xenoUniformBinders.put(layer, binder);
    }

    @Override
    public XenoUniformBinder xeno$getUniformBinder(ChunkSectionLayer layer) {
        return this.xenoUniformBinders.get(layer);
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void xenoOnClose(CallbackInfo ci) {
        this.xeno$clearBuffers();
    }
}
