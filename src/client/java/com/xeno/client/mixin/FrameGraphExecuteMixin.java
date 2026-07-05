package com.xeno.client.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import java.util.BitSet;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrameGraphBuilder.class)
public class FrameGraphExecuteMixin {
    @Shadow @Final private List<?> passes;
    @Shadow @Final private List<?> internalResources;

    @Inject(
        method = "execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void xeno_executeSequential(
        GraphicsResourceAllocator resourceAllocator,
        FrameGraphBuilder.Inspector inspector,
        CallbackInfo ci
    ) {
        for (Object resource : this.internalResources) {
            ((FrameGraphResourceAccessor) resource).xeno_acquire(resourceAllocator);
        }

        for (Object passObj : this.passes) {
            FrameGraphPassAccessor pass = (FrameGraphPassAccessor) passObj;

            inspector.beforeExecutePass(pass.xeno_getName());
            pass.xeno_getTask().run();
            inspector.afterExecutePass(pass.xeno_getName());

            BitSet toRelease = pass.xeno_getResourcesToRelease();
            for (int id = toRelease.nextSetBit(0); id >= 0; id = toRelease.nextSetBit(id + 1)) {
                ((FrameGraphResourceAccessor) this.internalResources.get(id)).xeno_release(resourceAllocator);
            }
        }

        ci.cancel();
    }
}
