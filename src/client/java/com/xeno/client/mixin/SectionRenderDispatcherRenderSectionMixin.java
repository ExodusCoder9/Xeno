package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoDispatcherAccess;
import com.xeno.client.renderer.XenoMeshArena;
import com.xeno.client.renderer.PendingUpload;
import com.xeno.client.renderer.MemoryIntrinsics;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
        XenoDispatcherAccess access = (XenoDispatcherAccess) this$0;
        XenoMeshArena arena = access.xeno$getArenas().get(layer);

        if (arena != null) {
            if (arena.isIntegrated()) {
                // Approach A: iGPU - direct copy from worker thread using MemoryIntrinsics
                long vSize = vertexBuffer != null ? vertexBuffer.remaining() : 0;
                long iSize = indexBuffer != null ? indexBuffer.remaining() : 0;
                XenoMeshArena.Allocation alloc = arena.allocate(key, vSize, iSize);

                if (vertexBuffer != null) {
                    long destAddress = alloc.segment().vertexBaseAddress + alloc.vertexSlot().offset;
                    MemoryIntrinsics.copy(vertexBuffer, destAddress, vSize);
                    access.xeno$getRenderThreadCallbacks().add(() -> this.vertexBufferUploadCallback(key, layer));
                }
                if (indexBuffer != null) {
                    long destAddress = alloc.segment().indexBaseAddress + alloc.indexSlot().offset;
                    MemoryIntrinsics.copy(indexBuffer, destAddress, iSize);
                    boolean sortedIndexBuffer = vertexBuffer == null;
                    access.xeno$getRenderThreadCallbacks().add(() -> this.indexBufferUploadCallback(key, layer, sortedIndexBuffer));
                }
            } else {
                // Approach B: dGPU - copy ByteBuffers and queue for render thread
                ByteBuffer vCopy = null;
                ByteBuffer iCopy = null;
                if (vertexBuffer != null) {
                    vCopy = ByteBuffer.allocateDirect(vertexBuffer.remaining()).put(vertexBuffer.duplicate()).flip();
                }
                if (indexBuffer != null) {
                    iCopy = ByteBuffer.allocateDirect(indexBuffer.remaining()).put(indexBuffer.duplicate()).flip();
                }
                
                final ByteBuffer finalVCopy = vCopy;
                final ByteBuffer finalICopy = iCopy;
                
                boolean sortedIndexBuffer = vertexBuffer == null;
                Runnable callback = () -> {
                    if (finalVCopy != null) {
                        this.vertexBufferUploadCallback(key, layer);
                    }
                    if (finalICopy != null) {
                        this.indexBufferUploadCallback(key, layer, sortedIndexBuffer);
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
            arena.free(oldMesh);
        }
        ci.cancel();
    }
}
