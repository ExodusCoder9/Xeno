package com.xeno.client.mixin;

import com.xeno.client.renderer.dispatcher.IXenoSectionRenderer;
import com.xeno.client.renderer.dispatcher.XenoRendererProvider;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection.class)
public abstract class SectionRenderDispatcherMixin {
    @Shadow @Final SectionRenderDispatcher this$0;

    @Inject(method = "compileSync", at = @At("HEAD"), cancellable = true)
    private void xenoCompileSync(RenderSectionRegion region, CallbackInfo ci) {
        ci.cancel();
        if (this.this$0 instanceof XenoRendererProvider provider) {
            IXenoSectionRenderer renderer = provider.xeno$getRenderer();
            if (renderer != null) {
                renderer.compileSectionSync((SectionRenderDispatcher.RenderSection) (Object) this, region);
            }
        }
    }

    @Inject(method = "compileAsync", at = @At("HEAD"), cancellable = true)
    private void xenoCompileAsync(RenderSectionRegion region, CallbackInfo ci) {
        ci.cancel();
        if (this.this$0 instanceof XenoRendererProvider provider) {
            IXenoSectionRenderer renderer = provider.xeno$getRenderer();
            if (renderer != null) {
                renderer.compileSectionAsync((SectionRenderDispatcher.RenderSection) (Object) this, region);
            }
        }
    }

    @Inject(method = "resortTransparency", at = @At("HEAD"), cancellable = true)
    private void xenoResortTransparency(CallbackInfo ci) {
        ci.cancel();
        if (this.this$0 instanceof XenoRendererProvider provider) {
            IXenoSectionRenderer renderer = provider.xeno$getRenderer();
            if (renderer != null) {
                renderer.resortTransparency((SectionRenderDispatcher.RenderSection) (Object) this);
            }
        }
    }
}