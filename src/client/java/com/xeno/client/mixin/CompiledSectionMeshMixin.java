package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoBufferPool;
import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.util.XenoMeshExtension;
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
    private final Map<ChunkSectionLayer, XenoBufferPool.Allocation> xenoVertexAllocations = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, XenoBufferPool.Allocation> xenoIndexAllocations = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private float[] xenoTranslucentQuadCenters;

    @Unique
    private int xenoTranslucentQuadCount;

    @Override
    public void xeno$setAllocations(ChunkSectionLayer layer, XenoBufferPool.Allocation vertexAlloc, XenoBufferPool.Allocation indexAlloc) {
        this.xenoVertexAllocations.put(layer, vertexAlloc);
        if (indexAlloc != null) {
            this.xenoIndexAllocations.put(layer, indexAlloc);
        }
    }

    @Override
    public XenoBufferPool.Allocation xeno$getVertexAllocation(ChunkSectionLayer layer) {
        return this.xenoVertexAllocations.get(layer);
    }

    @Override
    public XenoBufferPool.Allocation xeno$getIndexAllocation(ChunkSectionLayer layer) {
        return this.xenoIndexAllocations.get(layer);
    }

    @Override
    public void xeno$clearBuffers() {
        XenoWorldRenderer.freeAllocations(this.xenoVertexAllocations, this.xenoIndexAllocations);
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

    @Inject(method = "close", at = @At("RETURN"))
    private void xenoOnClose(CallbackInfo ci) {
        this.xeno$clearBuffers();
    }
}
