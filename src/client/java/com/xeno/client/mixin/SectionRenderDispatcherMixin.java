package com.xeno.client.mixin;

import com.xeno.client.culling.XenoVisibility;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public class SectionRenderDispatcherMixin {
    @Inject(method = "compileAsync", at = @At("HEAD"), cancellable = true)
    private void xenoSkipOccludedSections(RenderSectionRegion region, CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        if (XenoVisibility.isOccluded(self.getSectionNode())) {
            ci.cancel();
        }
    }
}
