package com.xeno.client.renderer.draw;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import org.lwjgl.opengl.GL;

/**
 * Native Hardware Multi-Draw Indirect (MDI) Dispatch Engine.
 * Executes single-call hardware draw passes across OpenGL 4.3+ and Vulkan 1.0+
 * bypassing CPU loop wrappings and Java Draw object overhead.
 */
public class XenoMdiEngine {

    public enum BackendType {
        OPENGL,
        VULKAN,
        UNKNOWN
    }

    private static BackendType cachedBackendType = null;

    public static BackendType getBackendType() {
        if (cachedBackendType == null) {
            try {
                String backendName = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase();
                if (backendName.contains("vulkan")) {
                    cachedBackendType = BackendType.VULKAN;
                } else if (backendName.contains("opengl")) {
                    cachedBackendType = BackendType.OPENGL;
                } else {
                    cachedBackendType = BackendType.UNKNOWN;
                }
            } catch (Throwable t) {
                try {
                    if (GL.getCapabilities().OpenGL43) {
                        cachedBackendType = BackendType.OPENGL;
                    } else {
                        cachedBackendType = BackendType.UNKNOWN;
                    }
                } catch (Throwable t2) {
                    cachedBackendType = BackendType.UNKNOWN;
                }
            }
        }
        return cachedBackendType;
    }

    public static int dispatchLayerMdi(ChunkSectionLayer layer, RenderPass pass) {
        if (pass == null) return 0;
        XenoMdiCommandBuffer mdiBuffer = XenoDrawListManager.getMdiBuffer(layer);
        int commandCount = mdiBuffer.getCommandCount();
        if (commandCount == 0) return 0;

        GpuBufferSlice commandsSlice = mdiBuffer.uploadToGpuSlice();
        if (commandsSlice == null) return 0;

        try {
            pass.drawIndexedIndirect(commandsSlice, commandCount);
            return commandCount;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int getGpuBufferHandle(GpuBuffer buffer) {
        if (buffer == null) return 0;
        try {
            // Reflective or accessor handle retrieval from Mojang GpuBuffer
            java.lang.reflect.Field field = buffer.getClass().getDeclaredField("handle");
            field.setAccessible(true);
            return field.getInt(buffer);
        } catch (Throwable t) {
            return 0;
        }
    }
}
