package com.xeno.client.mixin;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection.class)
public class SectionRenderDispatcherMixin {
    // Occlusion cancellation removed. Cancelling compilation here permanently
    // broke chunk state because the SectionUpdateTracker dirty flag is already cleared
    // by the time this is called, leading to chunks requiring a manual block update to appear.
}