package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
    private final Map<ChunkSectionLayer, GpuBuffer> xenoVertexBuffers = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Map<ChunkSectionLayer, GpuBuffer> xenoIndexBuffers = new EnumMap<>(ChunkSectionLayer.class);

    @Override
    public void xeno$setBuffers(ChunkSectionLayer layer, GpuBuffer vertexBuffer, GpuBuffer indexBuffer) {
        this.xenoVertexBuffers.put(layer, vertexBuffer);
        if (indexBuffer != null) {
            this.xenoIndexBuffers.put(layer, indexBuffer);
        }
    }

    @Override
    public GpuBuffer xeno$getVertexBuffer(ChunkSectionLayer layer) {
        return this.xenoVertexBuffers.get(layer);
    }

    @Override
    public GpuBuffer xeno$getIndexBuffer(ChunkSectionLayer layer) {
        return this.xenoIndexBuffers.get(layer);
    }

    @Override
    public void xeno$clearBuffers() {
        for (GpuBuffer buf : this.xenoVertexBuffers.values()) {
            if (buf != null && !buf.isClosed()) {
                buf.close();
            }
        }
        this.xenoVertexBuffers.clear();

        for (GpuBuffer buf : this.xenoIndexBuffers.values()) {
            if (buf != null && !buf.isClosed()) {
                buf.close();
            }
        }
        this.xenoIndexBuffers.clear();
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void xenoOnClose(CallbackInfo ci) {
        this.xeno$clearBuffers();
    }
}
