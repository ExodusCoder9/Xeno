package com.xeno.client.renderer.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Individual memory arena backing GPU VRAM or off-heap CPU memory allocations.
 */
public class XGenerationalArena {
    public final int arenaId;
    public final XenoGeneration generation;
    public final XenoMemoryKind memoryKind;
    public final long capacity;
    public final List<XGenerationalMultiBufferAllocator.AllocationHandle> allocations = new ArrayList<>();

    // GPU VRAM storage
    public final GpuBuffer gpuBuffer;

    // CPU Off-Heap FFM storage
    public final Arena ffmArena;
    public final MemorySegment memorySegment;
    public final ByteBuffer directBuffer;

    public long bumpOffset = 0L;
    public long activeBytes = 0L;

    public XGenerationalArena(
            int arenaId,
            XenoGeneration generation,
            XenoMemoryKind memoryKind,
            String namePrefix,
            int gpuUsageFlags,
            long capacity
    ) {
        this.arenaId = arenaId;
        this.generation = generation;
        this.memoryKind = memoryKind;
        this.capacity = capacity;

        if (memoryKind == XenoMemoryKind.GPU_VRAM) {
            this.gpuBuffer = RenderSystem.getDevice().createBuffer(
                    () -> namePrefix + "-" + generation.name() + "-Arena-" + arenaId,
                    gpuUsageFlags,
                    capacity
            );
            this.ffmArena = null;
            this.memorySegment = null;
            this.directBuffer = null;
        } else {
            this.gpuBuffer = null;
            this.ffmArena = Arena.ofShared();
            this.memorySegment = this.ffmArena.allocate(capacity, 16);
            this.directBuffer = this.memorySegment.asByteBuffer();
        }
    }

    public boolean canBumpAllocate(long alignedSize) {
        return this.bumpOffset + alignedSize <= this.capacity;
    }

    public XGenerationalMultiBufferAllocator.AllocationHandle bumpAllocate(
            long alignedSize,
            Object ownerTag,
            long allocId,
            long nowMs
    ) {
        long offset = this.bumpOffset;
        this.bumpOffset += alignedSize;
        this.activeBytes += alignedSize;

        XGenerationalMultiBufferAllocator.AllocationHandle handle =
                new XGenerationalMultiBufferAllocator.AllocationHandle(allocId, this, offset, alignedSize, ownerTag, nowMs);
        handle.indexInArena = this.allocations.size();
        this.allocations.add(handle);
        return handle;
    }

    public void removeAllocation(XGenerationalMultiBufferAllocator.AllocationHandle handle) {
        int idx = handle.indexInArena;
        int lastIdx = this.allocations.size() - 1;

        if (idx >= 0 && idx <= lastIdx && this.allocations.get(idx) == handle) {
            if (idx != lastIdx) {
                XGenerationalMultiBufferAllocator.AllocationHandle lastHandle = this.allocations.get(lastIdx);
                this.allocations.set(idx, lastHandle);
                lastHandle.indexInArena = idx;
            }
            this.allocations.remove(lastIdx);
            this.activeBytes -= handle.size;
            handle.indexInArena = -1;
        }

        // Reset bump offset when all active allocations in the arena are released
        if (this.activeBytes <= 0) {
            this.allocations.clear();
            this.bumpOffset = 0L;
            this.activeBytes = 0L;
        }
    }

    public double getFragmentationRatio() {
        if (this.bumpOffset == 0) return 0.0;
        long holeBytes = this.bumpOffset - this.activeBytes;
        return (double) holeBytes / (double) this.bumpOffset;
    }

    public void close() {
        if (this.gpuBuffer != null && !this.gpuBuffer.isClosed()) {
            this.gpuBuffer.close();
        }
        if (this.ffmArena != null && this.ffmArena.scope().isAlive()) {
            this.ffmArena.close();
        }
        this.allocations.clear();
        this.bumpOffset = 0L;
        this.activeBytes = 0L;
    }
}
