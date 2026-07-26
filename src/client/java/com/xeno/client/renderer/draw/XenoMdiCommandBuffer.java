package com.xeno.client.renderer.draw;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.memory.XGenerationalMultiBufferAllocator;

import java.lang.foreign.MemorySegment;

/**
 * Manages off-heap mapping and GPU VRAM upload of 20-byte MDI structs for Single-Call Indirect Drawing.
 * Hardware Layout per Command:
 * - uint count         (4 bytes): Index Count
 * - uint instanceCount  (4 bytes): Always 1
 * - uint firstIndex     (4 bytes): Index offset into Index Buffer
 * - int  baseVertex     (4 bytes): Vertex offset / 28 (DefaultVertexFormat.BLOCK)
 * - uint baseInstance   (4 bytes): Dynamic UBO Section Index for gl_BaseInstance
 */
public class XenoMdiCommandBuffer {

    public static final int COMMAND_STRIDE_BYTES = 20;
    private static final int INITIAL_COMMAND_CAPACITY = 8192; // Up to 8,192 visible sections per frame

    private XGenerationalMultiBufferAllocator.AllocationHandle currentAllocation;
    private XGenerationalMultiBufferAllocator.AllocationHandle currentGpuAllocation;
    private int commandCount = 0;

    public void beginFrame() {
        endFrame();
        this.currentAllocation = XenoWorldRenderer.getOffHeapBuildingPool().allocate(
                (long) INITIAL_COMMAND_CAPACITY * COMMAND_STRIDE_BYTES,
                "MdiCommandBuffer"
        );
    }

    public void writeCommand(int indexCount, int firstIndex, int baseVertex, int uboIndex) {
        if (this.currentAllocation == null) {
            beginFrame();
        }

        long byteOffset = (long) this.commandCount * COMMAND_STRIDE_BYTES;
        if (byteOffset + COMMAND_STRIDE_BYTES > this.currentAllocation.size) {
            // Expand allocation if needed
            long newSize = this.currentAllocation.size * 2;
            XGenerationalMultiBufferAllocator.AllocationHandle newAlloc = XenoWorldRenderer.getOffHeapBuildingPool().allocate(newSize, "MdiCommandBuffer");
            if (this.currentAllocation.getMemorySegment() != null && newAlloc.getMemorySegment() != null) {
                newAlloc.getMemorySegment().copyFrom(this.currentAllocation.getMemorySegment().asSlice(0, byteOffset));
            }
            XenoWorldRenderer.getOffHeapBuildingPool().free(this.currentAllocation);
            this.currentAllocation = newAlloc;
        }

        MemorySegment segment = this.currentAllocation.getMemorySegment();
        if (segment != null) {
            segment.set(java.lang.foreign.ValueLayout.JAVA_INT, byteOffset, indexCount);
            segment.set(java.lang.foreign.ValueLayout.JAVA_INT, byteOffset + 4L, 1); // instanceCount = 1
            segment.set(java.lang.foreign.ValueLayout.JAVA_INT, byteOffset + 8L, firstIndex);
            segment.set(java.lang.foreign.ValueLayout.JAVA_INT, byteOffset + 12L, baseVertex); // vertexOffset / 28
            segment.set(java.lang.foreign.ValueLayout.JAVA_INT, byteOffset + 16L, uboIndex); // gl_BaseInstance
        }

        this.commandCount++;
    }

    public int getCommandCount() {
        return this.commandCount;
    }

    public GpuBufferSlice uploadToGpuSlice() {
        if (this.commandCount == 0 || this.currentAllocation == null) return null;
        if (this.currentGpuAllocation != null) {
            XenoWorldRenderer.getIndirectBufferPool().free(this.currentGpuAllocation);
            this.currentGpuAllocation = null;
        }
        long totalBytes = (long) this.commandCount * COMMAND_STRIDE_BYTES;
        XGenerationalMultiBufferAllocator.AllocationHandle gpuAlloc = XenoWorldRenderer.getIndirectBufferPool().allocate(totalBytes, "MdiGpuBuffer");
        if (gpuAlloc != null && gpuAlloc.getBuffer() != null && this.currentAllocation.getMemorySegment() != null) {
            try (GpuBufferSlice.MappedView view = gpuAlloc.getBuffer().map(gpuAlloc.offset, totalBytes, false, true)) {
                MemorySegment.copy(this.currentAllocation.getMemorySegment(), 0L, java.lang.foreign.MemorySegment.ofBuffer(view.data()), 0L, totalBytes);
            }
            this.currentGpuAllocation = gpuAlloc;
            return gpuAlloc.getBuffer().slice(gpuAlloc.offset, totalBytes);
        }
        return null;
    }

    public void endFrame() {
        this.commandCount = 0;
        if (this.currentAllocation != null) {
            XenoWorldRenderer.getOffHeapBuildingPool().free(this.currentAllocation);
            this.currentAllocation = null;
        }
        if (this.currentGpuAllocation != null) {
            XenoWorldRenderer.getIndirectBufferPool().free(this.currentGpuAllocation);
            this.currentGpuAllocation = null;
        }
    }
}
