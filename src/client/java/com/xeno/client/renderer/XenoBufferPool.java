package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;

public class XenoBufferPool {
    private final String name;
    private final int usage;
    private final long bufferSize;

    private GpuBuffer currentBuffer;
    private final List<FreeBlock> freeBlocks = new ArrayList<>();
    private final List<GpuBuffer> allBuffers = new ArrayList<>();

    public static class FreeBlock {
        public long offset;
        public long size;

        public FreeBlock(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
    }

    public static class Allocation {
        public final GpuBuffer buffer;
        public final long offset;
        public final long size;

        public Allocation(GpuBuffer buffer, long offset, long size) {
            this.buffer = buffer;
            this.offset = offset;
            this.size = size;
        }
    }

    public XenoBufferPool(String name, int usage, long bufferSize) {
        this.name = name;
        this.usage = usage;
        this.bufferSize = bufferSize;
        this.createNewBuffer();
    }

    private void createNewBuffer() {
        this.currentBuffer = RenderSystem.getDevice().createBuffer(() -> this.name + "-" + this.allBuffers.size(), this.usage, this.bufferSize);
        this.allBuffers.add(this.currentBuffer);
        this.freeBlocks.add(new FreeBlock(0L, this.bufferSize));
    }

    public synchronized Allocation allocate(long size) {
        // Align allocations to 16 bytes for optimal GPU alignment
        long alignedSize = (size + 15) & ~15;

        for (int i = 0; i < this.freeBlocks.size(); i++) {
            FreeBlock block = this.freeBlocks.get(i);
            if (block.size >= alignedSize) {
                long offset = block.offset;
                if (block.size == alignedSize) {
                    this.freeBlocks.remove(i);
                } else {
                    block.offset += alignedSize;
                    block.size -= alignedSize;
                }
                return new Allocation(this.currentBuffer, offset, alignedSize);
            }
        }

        // Allocate a new buffer block if the current ones are exhausted
        this.createNewBuffer();
        FreeBlock block = this.freeBlocks.get(this.freeBlocks.size() - 1);
        long offset = block.offset;
        block.offset += alignedSize;
        block.size -= alignedSize;
        return new Allocation(this.currentBuffer, offset, alignedSize);
    }

    public synchronized boolean containsBuffer(GpuBuffer buffer) {
        return this.allBuffers.contains(buffer);
    }

    public synchronized void free(Allocation alloc) {
        if (alloc == null) return;

        // Check if the allocation fits into a known buffer (for safety)
        if (!this.allBuffers.contains(alloc.buffer)) {
            return;
        }

        long insertIndex = 0;
        boolean merged = false;

        for (int i = 0; i < this.freeBlocks.size(); i++) {
            FreeBlock block = this.freeBlocks.get(i);
            if (block.offset == alloc.offset + alloc.size) {
                block.offset = alloc.offset;
                block.size += alloc.size;
                merged = true;
                break;
            } else if (block.offset + block.size == alloc.offset) {
                block.size += alloc.size;
                if (i + 1 < this.freeBlocks.size()) {
                    FreeBlock nextBlock = this.freeBlocks.get(i + 1);
                    if (block.offset + block.size == nextBlock.offset) {
                        block.size += nextBlock.size;
                        this.freeBlocks.remove(i + 1);
                    }
                }
                merged = true;
                break;
            }

            if (block.offset < alloc.offset) {
                insertIndex = i + 1;
            }
        }

        if (!merged) {
            this.freeBlocks.add((int) insertIndex, new FreeBlock(alloc.offset, alloc.size));
        }
    }

    public synchronized void reset() {
        this.freeBlocks.clear();
        if (this.allBuffers.size() > 1) {
            for (int i = 1; i < this.allBuffers.size(); i++) {
                GpuBuffer buf = this.allBuffers.get(i);
                if (buf != null && !buf.isClosed()) {
                    buf.close();
                }
            }
            GpuBuffer first = this.allBuffers.get(0);
            this.allBuffers.clear();
            this.allBuffers.add(first);
            this.currentBuffer = first;
        }
        this.freeBlocks.add(new FreeBlock(0L, this.bufferSize));
    }

    public synchronized void close() {
        for (GpuBuffer buf : this.allBuffers) {
            if (buf != null && !buf.isClosed()) {
                buf.close();
            }
        }
        this.allBuffers.clear();
        this.freeBlocks.clear();
    }
}
