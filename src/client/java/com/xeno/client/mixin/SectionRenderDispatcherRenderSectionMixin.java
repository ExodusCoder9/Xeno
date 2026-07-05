package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoDispatcherAccess;
import com.xeno.client.renderer.XenoMeshArena;
import com.xeno.client.renderer.PendingUpload;
import com.xeno.client.renderer.MemoryIntrinsics;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;
import java.nio.ByteBuffer;

@Mixin(SectionRenderDispatcher.RenderSection.class)
@SuppressWarnings({"unused"})
public abstract class SectionRenderDispatcherRenderSectionMixin {
    @Shadow @Final
    SectionRenderDispatcher this$0;

    @Shadow
    protected abstract void vertexBufferUploadCallback(final CompiledSectionMesh sectionMesh, final ChunkSectionLayer layer);

    @Shadow
    protected abstract void indexBufferUploadCallback(final CompiledSectionMesh sectionMesh, final ChunkSectionLayer layer, final boolean sortedIndexBuffer);

    @Inject(method = "addSectionBuffersToUberBuffer", at = @At("HEAD"), cancellable = true)
    private void onAddSectionBuffersToUberBuffer(
            ChunkSectionLayer layer,
            CompiledSectionMesh key,
            @Nullable ByteBuffer vertexBuffer,
            @Nullable ByteBuffer indexBuffer,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SectionMesh.SectionDraw draw = key.getSectionDraw(layer);
        if (draw == null) {
            cir.setReturnValue(true);
            return;
        }

        XenoDispatcherAccess access = (XenoDispatcherAccess) this$0;
        XenoMeshArena arena = access.xeno$getArenas().get(layer);

        if (arena != null) {
            if (arena.isIntegrated()) {
                // Approach A: iGPU - direct copy from worker thread using MemoryIntrinsics
                if (vertexBuffer != null) {
                    long vSize = vertexBuffer.remaining();
                    XenoMeshArena.Allocation alloc = arena.allocateVertex(key, vSize);
                    long destAddress = alloc.segment().baseAddress + alloc.slot().offset;
                    MemoryIntrinsics.copy(vertexBuffer, destAddress, vSize);
                    access.xeno$getRenderThreadCallbacks().add(() -> this.vertexBufferUploadCallback(key, layer));
                }

                if (indexBuffer != null) {
                    long iSize = indexBuffer.remaining();
                    XenoMeshArena.Allocation alloc = arena.allocateIndex(key, iSize);
                    long destAddress = alloc.segment().baseAddress + alloc.slot().offset;
                    MemoryIntrinsics.copy(indexBuffer, destAddress, iSize);
                    boolean sortedIndexBuffer = vertexBuffer == null;
                    access.xeno$getRenderThreadCallbacks().add(() -> this.indexBufferUploadCallback(key, layer, sortedIndexBuffer));
                } else if (draw.hasCustomIndexBuffer()) {
                    //Only flag missing buffers as uploaded if they are actively tracked by the mesh
                    key.setIndexBufferUploaded(layer);
                }
            } else {
                // Approach B: dGPU - copy ByteBuffers using native malloc and queue for render thread
                ByteBuffer vCopy = null;
                ByteBuffer iCopy = null;

                if (vertexBuffer != null) {
                    int size = vertexBuffer.remaining();
                    vCopy = MemoryUtil.memAlloc(size);
                    MemoryUtil.memCopy(MemoryUtil.memAddress(vertexBuffer), MemoryUtil.memAddress(vCopy), size);
                }
                if (indexBuffer != null) {
                    int size = indexBuffer.remaining();
                    iCopy = MemoryUtil.memAlloc(size);
                    MemoryUtil.memCopy(MemoryUtil.memAddress(indexBuffer), MemoryUtil.memAddress(iCopy), size);
                }

                final ByteBuffer finalVCopy = vCopy;
                final ByteBuffer finalICopy = iCopy;
                boolean sortedIndexBuffer = vertexBuffer == null;

                Runnable callback = () -> {
                    // Mark index buffer as uploaded before firing the vertex callback
                    if (finalICopy == null && draw.hasCustomIndexBuffer()) {
                        key.setIndexBufferUploaded(layer);
                    }

                    if (finalVCopy != null) {
                        this.vertexBufferUploadCallback(key, layer);
                        MemoryUtil.memFree(finalVCopy);
                    }
                    if (finalICopy != null) {
                        this.indexBufferUploadCallback(key, layer, sortedIndexBuffer);
                        MemoryUtil.memFree(finalICopy);
                    }
                };

                access.xeno$getPendingUploads().add(new PendingUpload(key, layer, vCopy, iCopy, callback));
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "releaseSectionMesh", at = @At("HEAD"), cancellable = true)
    private void onReleaseSectionMesh(SectionMesh oldMesh, CallbackInfo ci) {
        oldMesh.close();
        XenoDispatcherAccess access = (XenoDispatcherAccess) this$0;
        for (XenoMeshArena arena : access.xeno$getArenas().values()) {
            arena.freeAll(oldMesh);
        }
        ci.cancel();
    }
}