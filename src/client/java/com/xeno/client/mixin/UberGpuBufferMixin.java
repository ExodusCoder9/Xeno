package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.xeno.client.renderer.XenoAllocator;
import com.xeno.client.renderer.XenoAllocationTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.ByteBuffer;
import java.util.Map;

@Mixin(value = UberGpuBuffer.class, remap = false)
@SuppressWarnings({"unchecked", "rawtypes"})
public class UberGpuBufferMixin {
    @Shadow @Final
    private Map allocationMap;

    @Inject(method = "addAllocation", at = @At("RETURN"))
    private void inject_addAllocation(Object key, Object callback, ByteBuffer data, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            TlsfAllocator.Allocation alloc = (TlsfAllocator.Allocation) this.allocationMap.get(key);
            if (alloc != null) {
                int usage = ((UberGpuBufferAccessor) this).getBufferUsage();
                boolean isVertex = (usage & GpuBuffer.USAGE_VERTEX) != 0;
                long offset;
                
                if (isVertex) {
                    offset = XenoAllocator.uploadVertex(key, data);
                } else {
                    offset = XenoAllocator.uploadIndex(key, data);
                }

                ((AllocationMixinAccessor) alloc).xeno_setOffset(offset);
                XenoAllocationTracker.register(key, alloc, isVertex);
            }
        }
    }

    @Inject(method = "removeAllocation", at = @At("HEAD"))
    private void inject_removeAllocation(Object key, CallbackInfo ci) {
        int usage = ((UberGpuBufferAccessor) this).getBufferUsage();
        boolean isVertex = (usage & GpuBuffer.USAGE_VERTEX) != 0;
        
        if (isVertex) {
            XenoAllocator.freeVertex(key);
        } else {
            XenoAllocator.freeIndex(key);
        }
        XenoAllocationTracker.unregister(key, isVertex);
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

    @Shadow
    private Map stagedAllocations;

    @Inject(method = "uploadStagedAllocations", at = @At("HEAD"), cancellable = true)
    private void inject_uploadStagedAllocations(GpuDevice device, Object uploader, CallbackInfoReturnable<Boolean> cir) {
        // Complete the staged uploads in place instantly (no staging copies to heap/old buffers)
        for (Object entryObj : this.stagedAllocations.values()) {
            try {
                // StagedAllocationEntry is private, so we use reflection to call callback().onUploadComplete()
                java.lang.reflect.Method callbackMethod = entryObj.getClass().getDeclaredMethod("callback");
                callbackMethod.setAccessible(true);
                Object callback = callbackMethod.invoke(entryObj);
                java.lang.reflect.Method onUploadComplete = callback.getClass().getDeclaredMethod("onUploadComplete");
                onUploadComplete.setAccessible(true);
                onUploadComplete.invoke(callback);
            } catch (Exception e) {
                // If anything fails, print error
                e.printStackTrace();
            }
        }
        this.stagedAllocations.clear();
        cir.setReturnValue(true);
    }
}
