/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.mixin;

import com.xeno.client.common.render.chunk.XenoSectionStagingCoordinator;
import com.xeno.client.common.render.chunk.XenoSectionTaskQueue;
import com.xeno.client.common.render.chunk.XenoStagingAccess;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(SectionRenderDispatcher.class)
public abstract class XenoSectionRenderDispatcherMixin implements XenoStagingAccess {
    @Shadow
    @Final
    private ReentrantLock copyLock;

    @Unique
    private Condition xeno$stagingNotFull;

    @Redirect(
        method = "<init>",
        at = @At(value = "NEW", target = "Lnet/minecraft/client/renderer/chunk/SectionTaskDynamicQueue;")
    )
    private static SectionTaskDynamicQueue xeno$useXenoTaskQueue() {
        return new XenoSectionTaskQueue();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xeno$onInit(CallbackInfo ci) {
        this.xeno$stagingNotFull = this.copyLock.newCondition();
        XenoSectionStagingCoordinator.setActiveDispatcher(this);
    }

    @Inject(method = "dispose", at = @At("HEAD"))
    private void xeno$onDispose(CallbackInfo ci) {
        XenoSectionStagingCoordinator.signalSpaceAvailable();
        XenoSectionStagingCoordinator.setActiveDispatcher(null);
    }

    @Inject(method = "clearCompileQueue", at = @At("RETURN"))
    private void xeno$onClearCompileQueue(CallbackInfo ci) {
        XenoSectionStagingCoordinator.signalSpaceAvailable();
    }

    @Inject(method = "uploadTerrainBuffersToGpu", at = @At("RETURN"))
    private void xeno$onUploadTerrainBuffers(CallbackInfo ci) {
        XenoSectionStagingCoordinator.signalSpaceAvailable();
    }

    @Override
    public void xeno$signalSpaceAvailable() {
        if (this.xeno$stagingNotFull != null) {
            if (this.copyLock.isHeldByCurrentThread()) {
                this.xeno$stagingNotFull.signalAll();
            } else {
                this.copyLock.lock();
                try {
                    this.xeno$stagingNotFull.signalAll();
                } finally {
                    this.copyLock.unlock();
                }
            }
        }
    }

    @Override
    public void xeno$awaitSpace() {
        if (this.xeno$stagingNotFull == null) {
            Thread.onSpinWait();
            return;
        }
        this.copyLock.lock();
        try {
            boolean signaled = this.xeno$stagingNotFull.await(1, TimeUnit.MILLISECONDS);
            if (!signaled) {
                Thread.onSpinWait();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            this.copyLock.unlock();
        }
    }
}
