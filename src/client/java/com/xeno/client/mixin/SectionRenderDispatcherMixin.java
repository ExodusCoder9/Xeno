package com.xeno.client.mixin;

import com.xeno.client.culling.XenoTaskQueue;
import com.xeno.client.renderer.XenoDispatcherAccess;
import com.xeno.client.renderer.XenoMeshArena;
import com.xeno.client.renderer.PendingUpload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.DeviceType;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.chunk.SectionMesh;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Util;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(SectionRenderDispatcher.class)
@SuppressWarnings({"unused"})
public class SectionRenderDispatcherMixin implements XenoDispatcherAccess {
    @Shadow @Final
    private SectionTaskDynamicQueue queue;
    @Shadow
    private volatile boolean closed;
    @Shadow @Final
    private ReentrantLock copyLock;

    @Unique
    private Map<ChunkSectionLayer, XenoMeshArena> xeno$arenas;
    @Unique
    private final ConcurrentLinkedQueue<PendingUpload> xeno$pendingUploads = new ConcurrentLinkedQueue<>();
    @Unique
    private final ConcurrentLinkedQueue<Runnable> xeno$renderThreadCallbacks = new ConcurrentLinkedQueue<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        GpuDevice device = RenderSystem.getDevice();
        boolean isIntegrated = device.getDeviceInfo().type() == DeviceType.INTEGRATED;
        this.xeno$arenas = Util.makeEnumMap(ChunkSectionLayer.class, layer -> {
            VertexFormat format = layer.pipeline().getVertexFormatBinding(0);
            return new XenoMeshArena(device, isIntegrated, 134217728, 33554432, format.getVertexSize(), 8);
        });
    }

    @Inject(method = "setCameraPosition", at = @At("HEAD"))
    private void xeno_updateCameraLook(Vec3 cameraPosition, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Vector3fc look = mc.gameRenderer.mainCamera().forwardVector();
        ((XenoTaskQueue) this.queue).xeno_updateCameraLook(look.x(), look.y(), look.z());
    }

    @Inject(method = "getRenderSectionSlice", at = @At("HEAD"), cancellable = true)
    private void onGetRenderSectionSlice(SectionMesh sectionMesh, ChunkSectionLayer layer, CallbackInfoReturnable<SectionRenderDispatcher.RenderSectionBufferSlice> cir) {
        XenoMeshArena arena = this.xeno$arenas.get(layer);
        if (arena != null) {
            cir.setReturnValue(arena.getSlice(sectionMesh));
        }
    }

    @Inject(method = "uploadTerrainBuffersToGpu", at = @At("HEAD"), cancellable = true)
    private void onUploadTerrainBuffersToGpu(CallbackInfo ci) {
        // 1. Run all render thread callbacks first (these update the meshes)
        Runnable callback;
        while ((callback = this.xeno$renderThreadCallbacks.poll()) != null) {
            callback.run();
        }

        // 2. Perform all pending uploads for dGPU (Approach B)
        PendingUpload upload;
        GpuDevice device = RenderSystem.getDevice();
        while ((upload = this.xeno$pendingUploads.poll()) != null) {
            XenoMeshArena arena = this.xeno$arenas.get(upload.layer());
            if (arena != null) {
                long vSize = upload.vertexData() != null ? upload.vertexData().remaining() : 0;
                long iSize = upload.indexData() != null ? upload.indexData().remaining() : 0;
                XenoMeshArena.Allocation alloc = arena.allocate(upload.mesh(), vSize, iSize);

                if (upload.vertexData() != null) {
                    device.createCommandEncoder().writeToBuffer(
                        alloc.segment().vertexBuffer.slice(alloc.vertexSlot().offset, vSize),
                        upload.vertexData()
                    );
                }
                if (upload.indexData() != null) {
                    device.createCommandEncoder().writeToBuffer(
                        alloc.segment().indexBuffer.slice(alloc.indexSlot().offset, iSize),
                        upload.indexData()
                    );
                }
                if (upload.callback() != null) {
                    upload.callback().run();
                }
            }
        }
        ci.cancel();
    }

    @Inject(method = "dispose", at = @At("HEAD"), cancellable = true)
    private void onDispose(CallbackInfo ci) {
        this.closed = true;
        this.queue.clear(); // clearCompileQueue
        this.copyLock.lock();
        try {
            for (XenoMeshArena arena : this.xeno$arenas.values()) {
                arena.close();
            }
        } finally {
            this.copyLock.unlock();
        }
        ci.cancel();
    }

    @Override
    public Map<ChunkSectionLayer, XenoMeshArena> xeno$getArenas() {
        return this.xeno$arenas;
    }

    @Override
    public ConcurrentLinkedQueue<PendingUpload> xeno$getPendingUploads() {
        return this.xeno$pendingUploads;
    }

    @Override
    public ConcurrentLinkedQueue<Runnable> xeno$getRenderThreadCallbacks() {
        return this.xeno$renderThreadCallbacks;
    }
}
