package com.xeno.client.mixin;

import com.xeno.client.renderer.memory.XenoMultiArenaAllocator;
import com.xeno.client.renderer.memory.IXenoArenaAllocator;
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
public abstract class CompiledSectionMeshMixin implements XenoMeshExtension, IXenoArenaAllocator.DefragListener {

    @Unique
    private final Map<ChunkSectionLayer, XenoMultiArenaAllocator.AllocationHandle> xenoVertexAllocations = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, XenoMultiArenaAllocator.AllocationHandle> xenoIndexAllocations = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, RenderPass.Draw<GpuBufferSlice[]>> xenoCachedDraws = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, XenoUniformBinder> xenoUniformBinders = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private float[] xenoTranslucentQuadCenters;

    @Unique
    private int xenoTranslucentQuadCount;

    @Override
    public void xeno$setAllocations(ChunkSectionLayer layer, XenoMultiArenaAllocator.AllocationHandle vertexAlloc, XenoMultiArenaAllocator.AllocationHandle indexAlloc) {
        this.xenoVertexAllocations.put(layer, vertexAlloc);
        if (indexAlloc != null) {
            this.xenoIndexAllocations.put(layer, indexAlloc);
        }
    }

    @Override
    public XenoMultiArenaAllocator.AllocationHandle xeno$getVertexAllocation(ChunkSectionLayer layer) {
        return this.xenoVertexAllocations.get(layer);
    }

    @Override
    public XenoMultiArenaAllocator.AllocationHandle xeno$getIndexAllocation(ChunkSectionLayer layer) {
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

    @Override
    public void onAllocationMoved(XenoMultiArenaAllocator.AllocationHandle handle, long oldOffset, long newOffset) {
        for (Map.Entry<ChunkSectionLayer, XenoMultiArenaAllocator.AllocationHandle> entry : this.xenoVertexAllocations.entrySet()) {
            if (entry.getValue() == handle) {
                ChunkSectionLayer layer = entry.getKey();
                RenderPass.Draw<GpuBufferSlice[]> oldDraw = this.xenoCachedDraws.get(layer);
                if (oldDraw != null) {
                    int baseVertex = (int) (newOffset / layer.pipeline().getVertexFormatBinding(0).getVertexSize());
                    RenderPass.Draw<GpuBufferSlice[]> newDraw = new RenderPass.Draw<>(
                            oldDraw.slot(), handle.getBuffer(), oldDraw.indexBuffer(), oldDraw.indexType(),
                            oldDraw.firstIndex(), oldDraw.indexCount(), baseVertex, oldDraw.uniformUploaderConsumer()
                    );
                    this.xenoCachedDraws.put(layer, newDraw);
                }
                return;
            }
        }
        for (Map.Entry<ChunkSectionLayer, XenoMultiArenaAllocator.AllocationHandle> entry : this.xenoIndexAllocations.entrySet()) {
            if (entry.getValue() == handle) {
                ChunkSectionLayer layer = entry.getKey();
                RenderPass.Draw<GpuBufferSlice[]> oldDraw = this.xenoCachedDraws.get(layer);
                if (oldDraw != null && oldDraw.indexType() != null) {
                    int firstIndex = (int) (newOffset / oldDraw.indexType().bytes);
                    RenderPass.Draw<GpuBufferSlice[]> newDraw = new RenderPass.Draw<>(
                            oldDraw.slot(), oldDraw.vertexBuffer(), handle.getBuffer(), oldDraw.indexType(),
                            firstIndex, oldDraw.indexCount(), oldDraw.baseVertex(), oldDraw.uniformUploaderConsumer()
                    );
                    this.xenoCachedDraws.put(layer, newDraw);
                }
                return;
            }
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void xenoOnClose(CallbackInfo ci) {
        this.xeno$clearBuffers();
    }
}
