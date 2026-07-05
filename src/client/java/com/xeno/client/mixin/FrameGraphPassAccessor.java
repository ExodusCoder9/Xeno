package com.xeno.client.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import java.util.BitSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.mojang.blaze3d.framegraph.FrameGraphBuilder$Pass")
public interface FrameGraphPassAccessor {
    @Accessor("name")
    String xeno_getName();

    @Accessor("task")
    Runnable xeno_getTask();

    @Accessor("resourcesToRelease")
    BitSet xeno_getResourcesToRelease();
}

@Mixin(targets = "com.mojang.blaze3d.framegraph.FrameGraphBuilder$InternalVirtualResource")
interface FrameGraphResourceAccessor {
    @Invoker("acquire")
    void xeno_acquire(GraphicsResourceAllocator allocator);

    @Invoker("release")
    void xeno_release(GraphicsResourceAllocator allocator);
}
