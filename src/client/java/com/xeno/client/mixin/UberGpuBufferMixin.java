package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.blaze3d.vertex.StagingBuffer;
import com.xeno.client.renderer.XenoAllocator;
import com.xeno.client.renderer.XenoAllocationTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.ByteBuffer;
import java.util.Map;

@Mixin(value = UberGpuBuffer.class, remap = false)
@SuppressWarnings("rawtypes")
public class UberGpuBufferMixin {
    @Shadow @Final
    private Map allocationMap;

    @Inject(method = "addAllocation", at = @At("RETURN"))
    private void inject_addAllocation(Object allocationKey, UberGpuBuffer.UploadCallback callback, ByteBuffer buffer, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            TlsfAllocator.Allocation alloc = (TlsfAllocator.Allocation) this.allocationMap.get(allocationKey);
            if (alloc != null) {
                int usage = ((UberGpuBufferAccessor) this).getBufferUsage();
                boolean isVertex = (usage & GpuBuffer.USAGE_VERTEX) != 0;
                long offset;
                
                if (isVertex) {
                    offset = XenoAllocator.uploadVertex(allocationKey, buffer);
                } else {
                    offset = XenoAllocator.uploadIndex(allocationKey, buffer);
                }

                ((AllocationMixinAccessor) alloc).xeno_setOffset(offset);
                XenoAllocationTracker.register(allocationKey, alloc, isVertex);
            }
        }
    }

    @Inject(method = "removeAllocation", at = @At("HEAD"))
    private void inject_removeAllocation(Object allocationKey, CallbackInfo ci) {
        int usage = ((UberGpuBufferAccessor) this).getBufferUsage();
        boolean isVertex = (usage & GpuBuffer.USAGE_VERTEX) != 0;
        
        if (isVertex) {
            XenoAllocator.freeVertex(allocationKey);
        } else {
            XenoAllocator.freeIndex(allocationKey);
        }
        XenoAllocationTracker.unregister(allocationKey, isVertex);
    }

    @Inject(method = "getGpuBuffer", at = @At("HEAD"), cancellable = true)
    private void inject_getGpuBuffer(TlsfAllocator.Allocation allocation, CallbackInfoReturnable<GpuBuffer> cir) {
        int usage = ((UberGpuBufferAccessor) this).getBufferUsage();
        if ((usage & GpuBuffer.USAGE_VERTEX) != 0) {
            cir.setReturnValue(XenoAllocator.getVertexBuffer());
        } else if ((usage & GpuBuffer.USAGE_INDEX) != 0) {
            cir.setReturnValue(XenoAllocator.getIndexBuffer());
        }
    }

    @Shadow @Final
    private it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap stagedAllocations;

    @Inject(method = "uploadStagedAllocations", at = @At("HEAD"), cancellable = true)
    private void inject_uploadStagedAllocations(GpuDevice gpuDevice, StagingBuffer.Uploader uploader, CallbackInfoReturnable<Boolean> cir) {
        // Complete the staged uploads in place instantly
        for (Object entryObj : this.stagedAllocations.values()) {
            try {
                // StagedAllocationEntry is private, so we use reflection to call callback().onUploadComplete()
                java.lang.reflect.Method callbackMethod = entryObj.getClass().getDeclaredMethod("callback");
                callbackMethod.setAccessible(true);
                Object callback = callbackMethod.invoke(entryObj);
                if (callback != null) {
                    java.lang.reflect.Method onUploadComplete = UberGpuBuffer.UploadCallback.class.getMethod("bufferHasBeenUploaded", Object.class);
                    onUploadComplete.setAccessible(true);
                    // Search for key of this entry
                    Object key = null;
                    for (Object entryObj2 : this.stagedAllocations.entrySet()) {
                        Map.Entry entry = (Map.Entry) entryObj2;
                        if (entry.getValue() == entryObj) {
                            key = entry.getKey();
                            break;
                        }
                    }
                    if (key != null) {
                        onUploadComplete.invoke(callback, key);
                    }
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().error("Failed to complete staged allocation callback in Xeno allocator", e);
            }
        }
        this.stagedAllocations.clear();
        cir.setReturnValue(true);
    }
}
