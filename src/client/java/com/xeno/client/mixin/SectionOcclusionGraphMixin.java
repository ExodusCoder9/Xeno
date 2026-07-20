package com.xeno.client.mixin;

import com.xeno.client.renderer.culling.IXenoCullingManager;
import com.xeno.client.renderer.culling.XenoCullingManager;
import com.xeno.client.renderer.culling.XenoCullingManagerProvider;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.util.VisibleForDebug;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin implements XenoCullingManagerProvider {
    @Shadow @Final
    private LongOpenHashSet emptySections;

    @Shadow @Final
    private LongOpenHashSet loadedChunks;

    @Unique
    private IXenoCullingManager xeno$cullingManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xenoOnInit(CallbackInfo ci) {
        this.xeno$cullingManager = new XenoCullingManager();
    }

    @Override
    public IXenoCullingManager xeno$getCullingManager() {
        return this.xeno$cullingManager;
    }

    @Inject(method = "waitAndReset", at = @At("HEAD"), cancellable = true)
    private void xenoWaitAndReset(@Nullable ViewArea viewArea, CallbackInfo ci) {
        ci.cancel();
        if (this.xeno$cullingManager != null) {
            this.xeno$cullingManager.waitAndReset(viewArea);
        }
    }

    @Inject(method = "expectedChunks", at = @At("HEAD"), cancellable = true)
    private void xenoExpectedChunks(CallbackInfoReturnable<LongCollection> cir) {
        cir.setReturnValue(LongSets.EMPTY_SET);
    }

    @Inject(method = "invalidate", at = @At("HEAD"), cancellable = true)
    private void xenoInvalidate(CallbackInfo ci) {
        ci.cancel();
        if (this.xeno$cullingManager != null) {
            this.xeno$cullingManager.invalidate();
        }
    }

    @Inject(method = "invalidateIfNeeded", at = @At("HEAD"), cancellable = true)
    private void xenoInvalidateIfNeeded(CameraRenderState camera, int fov, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "addSectionsInFrustum", at = @At("HEAD"), cancellable = true)
    private void xenoAddSectionsInFrustum(
            Frustum frustum,
            List<SectionRenderDispatcher.RenderSection> visibleSections,
            List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections,
            CallbackInfo ci
    ) {
        ci.cancel();
        if (this.xeno$cullingManager != null) {
            this.xeno$cullingManager.addSectionsInFrustum(frustum, visibleSections, nearbyVisibleSections);
        }
    }

    @Inject(method = "consumeFrustumUpdate", at = @At("HEAD"), cancellable = true)
    private void xenoConsumeFrustumUpdate(CallbackInfoReturnable<Boolean> cir) {
        if (this.xeno$cullingManager == null) {
            cir.setReturnValue(false);
        } else {
            cir.setReturnValue(this.xeno$cullingManager.consumeFrustumUpdate());
        }
    }

    @Inject(method = "schedulePropagationFrom", at = @At("HEAD"), cancellable = true)
    private void xenoSchedulePropagationFrom(
            SectionRenderDispatcher.RenderSection section,
            CallbackInfo ci
    ) {
        ci.cancel();
        if (this.xeno$cullingManager != null) {
            this.xeno$cullingManager.schedulePropagationFrom(section);
        }
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void xenoUpdate(
            CameraRenderState camera,
            int fov,
            ChunkLoadingRenderState chunkLoading,
            CallbackInfo ci
    ) {
        ci.cancel();
        if (this.xeno$cullingManager != null) {
            this.xeno$cullingManager.update(camera, fov, chunkLoading, this.emptySections, this.loadedChunks);
        }
    }

    @Inject(method = "getOctree", at = @At("HEAD"), cancellable = true)
    private void xenoGetOctree(CallbackInfoReturnable<@Nullable Octree> cir) {
        if (this.xeno$cullingManager == null) {
            cir.setReturnValue(null);
        } else {
            cir.setReturnValue(this.xeno$cullingManager.getOctree());
        }
    }

    @Inject(method = "getNode", at = @At("HEAD"), cancellable = true)
    @VisibleForDebug
    private void xenoGetNode(
            SectionRenderDispatcher.RenderSection section,
            CallbackInfoReturnable<SectionOcclusionGraph.Node> cir
    ) {
        cir.setReturnValue(null);
    }
}
