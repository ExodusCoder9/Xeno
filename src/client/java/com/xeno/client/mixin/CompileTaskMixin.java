package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.xeno.client.renderer.XenoDispatcherAccess;
import com.xeno.client.renderer.XenoMeshArena;
import com.xeno.client.renderer.PendingUpload;
import com.xeno.client.renderer.MemoryIntrinsics;
import java.util.Map;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import java.nio.ByteBuffer;

@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
public class CompileTaskMixin {
    @Shadow @Final
    SectionRenderDispatcher.RenderSection this$1;

    @Inject(
        method = "doTask",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;forEach(Ljava/util/function/BiConsumer;)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void xeno_uploadFluidMeshes(CallbackInfo ci) {
        SectionRenderDispatcher dispatcher = this.this$1.this$0;
        XenoDispatcherAccess access = (XenoDispatcherAccess) dispatcher;

        SectionCompiler.Results results = this.this$1.getCompiledResults();
        if (results == null) return;

        SectionCompilerResultsMixin r = (SectionCompilerResultsMixin) (Object) results;
        if (r.xeno$fluidLayers == null || r.xeno$fluidLayers.isEmpty()) return;

        CompiledSectionMesh mesh = this.this$1.getCompiledSectionMesh();
        if (mesh == null) return;

        for (Map.Entry<ChunkSectionLayer, MeshData> entry : r.xeno$fluidLayers.entrySet()) {
            ChunkSectionLayer layer = entry.getKey();
            MeshData fluidMesh = entry.getValue();
            if (fluidMesh == null) continue;

            ByteBuffer vertexData = fluidMesh.vertexBuffer();
            ByteBuffer indexData = fluidMesh.indexBuffer();

            if (vertexData == null) continue;

            XenoMeshArena arena = access.xeno$getArenas().get(layer);
            if (arena == null) continue;

            if (arena.isIntegrated()) {
                long vSize = vertexData.remaining();
                XenoMeshArena.Allocation alloc = arena.allocateVertex(mesh, vSize);
                long destAddress = alloc.segment().baseAddress + alloc.slot().offset;
                MemoryIntrinsics.copy(vertexData, destAddress, vSize);

                if (indexData != null) {
                    long iSize = indexData.remaining();
                    XenoMeshArena.Allocation iAlloc = arena.allocateIndex(mesh, iSize);
                    long iDestAddress = iAlloc.segment().baseAddress + iAlloc.slot().offset;
                    MemoryIntrinsics.copy(indexData, iDestAddress, iSize);
                }
            } else {
                ByteBuffer vCopy = null;
                ByteBuffer iCopy = null;

                if (vertexData != null) {
                    int size = vertexData.remaining();
                    vCopy = MemoryUtil.memAlloc(size);
                    MemoryUtil.memCopy(MemoryUtil.memAddress(vertexData), MemoryUtil.memAddress(vCopy), size);
                }
                if (indexData != null) {
                    int size = indexData.remaining();
                    iCopy = MemoryUtil.memAlloc(size);
                    MemoryUtil.memCopy(MemoryUtil.memAddress(indexData), MemoryUtil.memAddress(iCopy), size);
                }

                final ByteBuffer finalVCopy = vCopy;
                final ByteBuffer finalICopy = iCopy;

                access.xeno$getPendingUploads().add(new PendingUpload(mesh, layer, finalVCopy, finalICopy, () -> {
                    if (finalVCopy != null) MemoryUtil.memFree(finalVCopy);
                    if (finalICopy != null) MemoryUtil.memFree(finalICopy);
                }));
            }
        }
    }
}
