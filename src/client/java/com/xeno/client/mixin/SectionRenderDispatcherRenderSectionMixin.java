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
            boolean success = true;

            // Integrated path, copy directly to mapped memory, then queue callbacks.
            if (arena.isIntegrated()) {
                if (vertexBuffer != null) {
                    long vSize = vertexBuffer.remaining();
                    XenoMeshArena.Allocation alloc = arena.allocateVertex(key, vSize);
                    if (alloc == null) {
                        success = false;
                    } else {
                        long destAddress = alloc.segment().baseAddress + alloc.slot().offset;
                        MemoryIntrinsics.copy(vertexBuffer, destAddress, vSize);
                        access.xeno$getRenderThreadCallbacks().add(() -> {
                            if (key.getSectionDraw(layer) != null) {
                                vertexBufferUploadCallback(key, layer);
                            }
                        });
                    }
                }

                if (indexBuffer != null) {
                    long iSize = indexBuffer.remaining();
                    XenoMeshArena.Allocation alloc = arena.allocateIndex(key, iSize);
                    if (alloc == null) {
                        success = false;
                    } else {
                        long destAddress = alloc.segment().baseAddress + alloc.slot().offset;
                        MemoryIntrinsics.copy(indexBuffer, destAddress, iSize);
                        boolean sortedIndexBuffer = vertexBuffer == null;
                        access.xeno$getRenderThreadCallbacks().add(() -> {
                            if (key.getSectionDraw(layer) != null) {
                                indexBufferUploadCallback(key, layer, sortedIndexBuffer);
                            }
                        });
                    }
                } else {
                    // No index buffer to set uploaded immediately.
                    key.setIndexBufferUploaded(layer);
                }

                if (!success) {
                    // Allocation failure so signal the caller to retry after an upload.
                    cir.setReturnValue(false);
                    return;
                }
                cir.setReturnValue(true);
                return;
            }

            // Non‑integrated path, copy to heap, queue upload with callback.
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

            // If no index buffer, mark it uploaded immediately.
            if (indexBuffer == null) {
                key.setIndexBufferUploaded(layer);
            }

            Runnable callback = () -> {
                if (key.getSectionDraw(layer) == null) {
                    if (finalVCopy != null) MemoryUtil.memFree(finalVCopy);
                    if (finalICopy != null) MemoryUtil.memFree(finalICopy);
                    return;
                }

                if (finalVCopy != null) {
                    vertexBufferUploadCallback(key, layer);
                    MemoryUtil.memFree(finalVCopy);
                }
                if (finalICopy != null) {
                    indexBufferUploadCallback(key, layer, sortedIndexBuffer);
                    MemoryUtil.memFree(finalICopy);
                }
            };

            access.xeno$getPendingUploads().add(new PendingUpload(key, layer, vCopy, iCopy, callback));
            cir.setReturnValue(true);
            return;
        }

        // Fallback.
        cir.setReturnValue(false);
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