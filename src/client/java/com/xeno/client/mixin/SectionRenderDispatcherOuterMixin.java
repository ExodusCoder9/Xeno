package com.xeno.client.mixin;

import com.xeno.client.renderer.dispatcher.IXenoSectionRenderer;
import com.xeno.client.renderer.dispatcher.XenoRendererProvider;
import com.xeno.client.renderer.dispatcher.XenoSectionRenderer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.StagingBuffer;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.TracingExecutor;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.class)
public class SectionRenderDispatcherOuterMixin implements XenoRendererProvider {

    @Unique
    private IXenoSectionRenderer xeno$renderer;

    @Inject(method = "<init>", at = @At("HEAD"))
    private void onInit(
            TracingExecutor executor,
            RenderBuffers renderBuffers,
            SectionCompiler sectionCompiler,
            Consumer<SectionRenderDispatcher.RenderSection> onSectionMeshUpdate,
            CallbackInfo ci
    ) {
        this.xeno$renderer = new XenoSectionRenderer(sectionCompiler, onSectionMeshUpdate);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/StagingBuffer;create(Ljava/lang/String;Lcom/mojang/blaze3d/systems/GpuDevice;I)Lcom/mojang/blaze3d/vertex/StagingBuffer;"))
    private StagingBuffer redirectStagingBufferCreate(String name, GpuDevice device, int size) {
        // Return null to completely bypass staging buffer creation
        return null;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;makeEnumMap(Ljava/lang/Class;Ljava/util/function/Function;)Ljava/util/Map;"))
    private Map redirectMakeEnumMap(Class clazz, Function function) {
        // Return an empty EnumMap to completely bypass creating UberGpuBuffers
        return new java.util.EnumMap<>(clazz);
    }

    @Override
    public IXenoSectionRenderer xeno$getRenderer() {
        return this.xeno$renderer;
    }

    @Override
    public void xeno$setRenderer(IXenoSectionRenderer renderer) {
        this.xeno$renderer = renderer;
    }

    @Inject(method = "setCompiler", at = @At("HEAD"), cancellable = true)
    private void onSetCompiler(SectionCompiler sectionCompiler, CallbackInfo ci) {
        if (this.xeno$renderer != null) {
            this.xeno$renderer.setCompiler(sectionCompiler);
            ci.cancel();
        }
    }

    @Inject(method = "getRenderSectionSlice", at = @At("HEAD"), cancellable = true)
    private void onGetRenderSectionSlice(SectionMesh sectionMesh, ChunkSectionLayer layer, CallbackInfoReturnable<SectionRenderDispatcher.RenderSectionBufferSlice> cir) {
        if (this.xeno$renderer != null) {
            cir.setReturnValue(this.xeno$renderer.getRenderSectionSlice(sectionMesh, layer));
        }
    }

    @Inject(method = "uploadTerrainBuffersToGpu", at = @At("HEAD"), cancellable = true)
    private void onUploadTerrainBuffersToGpu(CallbackInfo ci) {
        if (this.xeno$renderer != null) {
            this.xeno$renderer.uploadTerrainBuffersToGpu();
            ci.cancel();
        }
    }

    @Inject(method = "clearCompileQueue", at = @At("HEAD"), cancellable = true)
    private void onClearCompileQueue(CallbackInfo ci) {
        if (this.xeno$renderer != null) {
            this.xeno$renderer.clearCompileQueue();
            ci.cancel();
        }
    }

    @Inject(method = "isQueueEmpty", at = @At("HEAD"), cancellable = true)
    private void onIsQueueEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (this.xeno$renderer != null) {
            cir.setReturnValue(this.xeno$renderer.isQueueEmpty());
        }
    }

    @Inject(method = "dispose", at = @At("HEAD"), cancellable = true)
    private void onDispose(CallbackInfo ci) {
        if (this.xeno$renderer != null) {
            this.xeno$renderer.dispose();
            ci.cancel();
        }
    }

    @Inject(method = "getStats", at = @At("HEAD"), cancellable = true)
    private void onGetStats(CallbackInfoReturnable<String> cir) {
        if (this.xeno$renderer != null) {
            cir.setReturnValue(this.xeno$renderer.getStats());
        }
    }

    @Inject(method = "getCompileQueueSize", at = @At("HEAD"), cancellable = true)
    private void onGetCompileQueueSize(CallbackInfoReturnable<Integer> cir) {
        if (this.xeno$renderer != null) {
            cir.setReturnValue(this.xeno$renderer.getCompileQueueSize());
        }
    }

    @Inject(method = "getFreeBufferCount", at = @At("HEAD"), cancellable = true)
    private void onGetFreeBufferCount(CallbackInfoReturnable<Integer> cir) {
        if (this.xeno$renderer != null) {
            cir.setReturnValue(this.xeno$renderer.getFreeBufferCount());
        }
    }

    @Inject(method = "setCameraPosition", at = @At("HEAD"), cancellable = true)
    private void onSetCameraPosition(Vec3 cameraPosition, CallbackInfo ci) {
        if (this.xeno$renderer != null) {
            this.xeno$renderer.setCameraPosition(cameraPosition);
            ci.cancel();
        }
    }
}
