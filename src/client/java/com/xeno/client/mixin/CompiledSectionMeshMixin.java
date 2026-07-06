package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public class CompiledSectionMeshMixin {
    @Unique
    private Map<ChunkSectionLayer, SectionMesh.SectionDraw> xeno$fluidDraws;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xeno_initFluidDraws(CompiledSectionMesh.TranslucencyPointOfView translucencyPointOfView, SectionCompiler.Results results, CallbackInfo ci) {
        SectionCompilerResultsMixin r = (SectionCompilerResultsMixin) (Object) results;
        if (r.xeno$fluidLayers != null && !r.xeno$fluidLayers.isEmpty()) {
            this.xeno$fluidDraws = new EnumMap<>(ChunkSectionLayer.class);
            for (Map.Entry<ChunkSectionLayer, MeshData> entry : r.xeno$fluidLayers.entrySet()) {
                ChunkSectionLayer layer = entry.getKey();
                MeshData mesh = entry.getValue();
                if (mesh != null) {
                    this.xeno$fluidDraws.put(layer, new SectionMesh.SectionDraw(
                        mesh.drawState().indexCount(), mesh.drawState().indexType(), mesh.indexBuffer() != null
                    ));
                }
            }
        }
    }

    public @Nullable SectionMesh.SectionDraw xeno$getFluidSectionDraw(ChunkSectionLayer layer) {
        return this.xeno$fluidDraws != null ? this.xeno$fluidDraws.get(layer) : null;
    }
}
