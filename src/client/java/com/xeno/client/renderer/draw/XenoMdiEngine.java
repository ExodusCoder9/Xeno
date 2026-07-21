package com.xeno.client.renderer.draw;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.memory.XGenerationalMultiBufferAllocator;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL43C;

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
        XenoMdiCommandBuffer mdiBuffer = XenoDrawListManager.getMdiBuffer(layer);
        int commandCount = mdiBuffer.getCommandCount();
        if (commandCount == 0) return 0;

        GpuBufferSlice commandsSlice = mdiBuffer.uploadToGpuSlice();
        if (commandsSlice == null) return 0;

        BackendType backend = getBackendType();

        if (backend == BackendType.VULKAN && pass != null) {
            // Direct Native Vulkan MDI (Blaze3D maps drawIndexedIndirect directly to vkCmdDrawIndexedIndirect)
            try {
                pass.drawIndexedIndirect(commandsSlice, commandCount);
                return commandCount;
            } catch (Throwable ignored) {
            }
        } else if (backend == BackendType.OPENGL) {
            XGenerationalMultiBufferAllocator.AllocationHandle vertexAlloc = XenoWorldRenderer.getVertexBufferPool().allocate(1L, "Check");
            XGenerationalMultiBufferAllocator.AllocationHandle indexAlloc = XenoWorldRenderer.getIndexBufferPool().allocate(1L, "Check");

            if (vertexAlloc != null && indexAlloc != null) {
                int indirectBufferId = getGpuBufferHandle(commandsSlice.buffer());
                int vertexBufferId = getGpuBufferHandle(vertexAlloc.getBuffer());
                int indexBufferId = getGpuBufferHandle(indexAlloc.getBuffer());

                if (indirectBufferId > 0 && vertexBufferId > 0 && indexBufferId > 0) {
                    GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vertexBufferId);
                    GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
                    GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, indirectBufferId);

                    GL43C.glMultiDrawElementsIndirect(
                            GL11C.GL_TRIANGLES,
                            GL11C.GL_UNSIGNED_SHORT,
                            commandsSlice.offset(),
                            commandCount,
                            XenoMdiCommandBuffer.COMMAND_STRIDE_BYTES
                    );

                    GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, 0);
                    return commandCount;
                }
            }
        }

        // Universal Fallback Pass
        if (pass != null) {
            try {
                pass.drawIndexedIndirect(commandsSlice, commandCount);
            } catch (Throwable ignored) {
            }
        }
        return commandCount;
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
