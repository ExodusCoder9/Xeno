package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TlsfAllocator.Allocation.class)
public class AllocationMixin implements AllocationMixinAccessor {
    @Unique
    private long xenoOffset = -1;

    @Override
    public void xeno_setOffset(long offset) {
        this.xenoOffset = offset;
    }

    @Inject(method = "getOffsetFromHeap", at = @At("HEAD"), cancellable = true)
    private void inject_getOffsetFromHeap(CallbackInfoReturnable<Long> cir) {
        if (this.xenoOffset != -1) {
            cir.setReturnValue(this.xenoOffset);
        }
    }
}
