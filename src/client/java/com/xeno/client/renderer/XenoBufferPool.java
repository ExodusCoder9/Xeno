package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;

public class XenoBufferPool {
    private final String name;
    private final int usage;
    private final long bufferSize;

    private final List<BufferBlock> blocks = new ArrayList<>();

    public static class FreeBlock {
        public long offset;
        public long size;

        public FreeBlock(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
    }

    public static class BufferBlock {
        public final GpuBuffer buffer;
        public final List<FreeBlock> freeBlocks = new ArrayList<>();

        public BufferBlock(GpuBuffer buffer, long size) {
            this.buffer = buffer;
            this.freeBlocks.add(new FreeBlock(0L, size));
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
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> this.name + "-" + this.blocks.size(),
                this.usage,
                this.bufferSize
        );
        this.blocks.add(new BufferBlock(buffer, this.bufferSize));
    }

    public synchronized boolean containsBuffer(GpuBuffer buffer) {
        for (BufferBlock block : this.blocks) {
            if (block.buffer == buffer) {
                return true;
            }
        }
        return false;
    }

    public synchronized Allocation allocate(long size) {
        long alignedSize = (size + 15) & ~15;

        // Try to allocate from existing blocks
        for (BufferBlock block : this.blocks) {
            List<FreeBlock> freeList = block.freeBlocks;
            for (int i = 0; i < freeList.size(); i++) {
                FreeBlock fb = freeList.get(i);
                if (fb.size >= alignedSize) {
                    long offset = fb.offset;
                    if (fb.size == alignedSize) {
                        freeList.remove(i);
                    } else {
                        fb.offset += alignedSize;
                        fb.size -= alignedSize;
                    }
                    return new Allocation(block.buffer, offset, alignedSize);
                }
            }
        }

        // All existing blocks are full, allocate a new block
        this.createNewBuffer();
        BufferBlock newBlock = this.blocks.get(this.blocks.size() - 1);
        FreeBlock fb = newBlock.freeBlocks.get(0);
        long offset = fb.offset;
        fb.offset += alignedSize;
        fb.size -= alignedSize;
        return new Allocation(newBlock.buffer, offset, alignedSize);
    }

    public synchronized void free(Allocation alloc) {
        if (alloc == null) return;

        // Find the block that owns this allocation
        BufferBlock targetBlock = null;
        for (BufferBlock block : this.blocks) {
            if (block.buffer == alloc.buffer) {
                targetBlock = block;
                break;
            }
        }

        if (targetBlock == null) {
            return;
        }

        List<FreeBlock> freeList = targetBlock.freeBlocks;
        long insertIndex = 0;
        boolean merged = false;

        for (int i = 0; i < freeList.size(); i++) {
            FreeBlock block = freeList.get(i);
            if (block.offset == alloc.offset + alloc.size) {
                block.offset = alloc.offset;
                block.size += alloc.size;
                merged = true;
                break;
            } else if (block.offset + block.size == alloc.offset) {
                block.size += alloc.size;
                if (i + 1 < freeList.size()) {
                    FreeBlock nextBlock = freeList.get(i + 1);
                    if (block.offset + block.size == nextBlock.offset) {
                        block.size += nextBlock.size;
                        freeList.remove(i + 1);
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
            freeList.add((int) insertIndex, new FreeBlock(alloc.offset, alloc.size));
        }
    }

    public synchronized void reset() {
        // Keep only the first block, close the rest to reclaim memory
        if (this.blocks.size() > 1) {
            for (int i = 1; i < this.blocks.size(); i++) {
                GpuBuffer buf = this.blocks.get(i).buffer;
                if (buf != null && !buf.isClosed()) {
                    buf.close();
                }
            }
            BufferBlock first = this.blocks.get(0);
            this.blocks.clear();
            this.blocks.add(first);
        }

        // Reset the free list of the first block
        BufferBlock firstBlock = this.blocks.get(0);
        firstBlock.freeBlocks.clear();
        firstBlock.freeBlocks.add(new FreeBlock(0L, this.bufferSize));
    }

    public synchronized void close() {
        for (BufferBlock block : this.blocks) {
            if (block.buffer != null && !block.buffer.isClosed()) {
                block.buffer.close();
            }
        }
        this.blocks.clear();
    }
}
