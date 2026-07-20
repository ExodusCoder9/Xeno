package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import com.xeno.client.renderer.XenoSectionRenderDispatcher;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.xeno.client.renderer.util.XenoMeshExtension;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection.class)
public abstract class SectionRenderDispatcherMixin {
    @Shadow @Final SectionRenderDispatcher this$0;
    @Shadow @Final public int index;
    @Shadow @Final public AtomicReference<SectionMesh> sectionMesh;
    @Shadow public abstract long getSectionNode();
    @Shadow public abstract BlockPos getRenderOrigin();

    @Inject(method = "compileSync", at = @At("HEAD"), cancellable = true)
    private void xenoCompileSync(RenderSectionRegion region, CallbackInfo ci) {
        ci.cancel();
        this.xenoCompile(region);
    }

    @Inject(method = "compileAsync", at = @At("HEAD"), cancellable = true)
    private void xenoCompileAsync(RenderSectionRegion region, CallbackInfo ci) {
        ci.cancel();
        CompletableFuture.runAsync(() -> {
            this.xenoCompile(region);
        }, Util.backgroundExecutor());
    }

    @Unique
    private void xenoCompile(RenderSectionRegion region) {
        if (region == null) return;
        if (!(this.this$0 instanceof XenoSectionRenderDispatcher dispatcher)) return;

        SectionCompiler compiler = dispatcher.getCompiler();
        if (compiler == null) return;

        SectionPos sectionPos = SectionPos.of(this.getSectionNode());
        
        // Acquire builder pack from dispatcher's synchronized pool
        SectionBufferBuilderPack builders = dispatcher.acquirePack();
        builders.discardAll(); // Silently reset builders to start fresh without warnings

        SectionCompiler.Results results = null;
        try {
            Vec3 cameraPos = XenoClient.getCameraPos();
            float rx = 0;
            float ry = 0;
            float rz = 0;
            if (cameraPos != null) {
                BlockPos origin = this.getRenderOrigin();
                rx = (float) (cameraPos.x - origin.getX());
                ry = (float) (cameraPos.y - origin.getY());
                rz = (float) (cameraPos.z - origin.getZ());
            }
            VertexSorting vertexSorting = VertexSorting.byDistance(rx, ry, rz);

            results = compiler.compile(sectionPos, region, vertexSorting, builders);
        } catch (Throwable t) {
            // Silently absorb exceptions during reload as the region is invalidated
        } finally {
            if (results != null) {
                dispatcher.getUploadQueue().add(new XenoSectionRenderDispatcher.UploadTask(
                        (SectionRenderDispatcher.RenderSection) (Object) this,
                        results,
                        builders
                ));
            } else {
                // If compilation failed/cancelled, safely return the builders pack back to the pool
                dispatcher.releasePack(builders);
            }
        }
    }

    @Inject(method = "resortTransparency", at = @At("HEAD"), cancellable = true)
    private void xenoResortTransparency(CallbackInfo ci) {
        ci.cancel();

        SectionMesh mesh = this.sectionMesh.get();
        if (mesh instanceof CompiledSectionMesh compiled && mesh instanceof XenoMeshExtension ext) {
            com.xeno.client.renderer.XenoBufferPool.Allocation indexAlloc = ext.xeno$getIndexAllocation(net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
            com.xeno.client.renderer.TranslucentSorter.resort(compiled, ext, this.getSectionNode(), this.getRenderOrigin(), indexAlloc);
        }
    }
}