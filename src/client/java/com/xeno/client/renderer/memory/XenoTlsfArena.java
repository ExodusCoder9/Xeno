package com.xeno.client.renderer.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Two-Level Segregated Fit (TLSF) memory arena for O(1) time-bounded allocations
 * and immediate boundary-tag coalescing in Old Generation VRAM/Off-Heap memory.
 */
public class XenoTlsfArena extends XGenerationalArena {

    private static final int MAX_FL_INDEX = 30; // Up to 1GB per arena
    private static final int SL_INDEX_COUNT = 16; // 16 subdivisions per power-of-two class
    private static final int SL_SHIFT = 4;
    private static final int MIN_BLOCK_SIZE = 16; // 16-byte alignment

    public static class BlockHeader {
        public long offset;
        public long size;
        public boolean free;
        public BlockHeader prevPhysical;
        public BlockHeader nextPhysical;
        public BlockHeader prevFree;
        public BlockHeader nextFree;
        public int flIndex = -1;
        public int slIndex = -1;

        public BlockHeader(long offset, long size) {
            this.offset = offset;
            this.size = size;
            this.free = true;
        }
    }

    private int flBitmap = 0;
    private final int[] slBitmap = new int[MAX_FL_INDEX];
    private final BlockHeader[][] matrix = new BlockHeader[MAX_FL_INDEX][SL_INDEX_COUNT];

    public XenoTlsfArena(
            int arenaId,
            XenoGeneration generation,
            XenoMemoryKind memoryKind,
            String namePrefix,
            int gpuUsageFlags,
            long capacity
    ) {
        super(arenaId, generation, memoryKind, namePrefix, gpuUsageFlags, capacity);

        // Initialize single initial free block covering the full capacity
        BlockHeader initialBlock = new BlockHeader(0L, capacity);
        insertFreeBlock(initialBlock);
    }

    private static int getFlIndex(long size) {
        if (size < MIN_BLOCK_SIZE) size = MIN_BLOCK_SIZE;
        return 63 - Long.numberOfLeadingZeros(size);
    }

    private static int getSlIndex(long size, int fl) {
        return (int) ((size ^ (1L << fl)) >>> (fl - SL_SHIFT));
    }

    private synchronized void insertFreeBlock(BlockHeader block) {
        block.free = true;
        int fl = getFlIndex(block.size);
        int sl = getSlIndex(block.size, fl);

        block.flIndex = fl;
        block.slIndex = sl;
        block.prevFree = null;
        block.nextFree = this.matrix[fl][sl];

        if (this.matrix[fl][sl] != null) {
            this.matrix[fl][sl].prevFree = block;
        }
        this.matrix[fl][sl] = block;

        this.flBitmap |= (1 << fl);
        this.slBitmap[fl] |= (1 << sl);
    }

    private synchronized void removeFreeBlock(BlockHeader block) {
        int fl = block.flIndex;
        int sl = block.slIndex;

        if (block.prevFree != null) {
            block.prevFree.nextFree = block.nextFree;
        } else {
            this.matrix[fl][sl] = block.nextFree;
        }

        if (block.nextFree != null) {
            block.nextFree.prevFree = block.prevFree;
        }

        if (this.matrix[fl][sl] == null) {
            this.slBitmap[fl] &= ~(1 << sl);
            if (this.slBitmap[fl] == 0) {
                this.flBitmap &= ~(1 << fl);
            }
        }

        block.free = false;
        block.prevFree = null;
        block.nextFree = null;
    }

    public synchronized BlockHeader allocateTlsf(long alignedSize) {
        int fl = getFlIndex(alignedSize);
        int sl = getSlIndex(alignedSize, fl);

        int map = this.slBitmap[fl] & (~0 << sl);
        if (map == 0) {
            int flMap = this.flBitmap & (~0 << (fl + 1));
            if (flMap == 0) {
                return null; // Arena capacity exhausted
            }
            fl = Integer.numberOfTrailingZeros(flMap);
            map = this.slBitmap[fl];
        }
        sl = Integer.numberOfTrailingZeros(map);

        BlockHeader block = this.matrix[fl][sl];
        removeFreeBlock(block);

        // Split block if remaining space exceeds MIN_BLOCK_SIZE
        long remaining = block.size - alignedSize;
        if (remaining >= MIN_BLOCK_SIZE) {
            block.size = alignedSize;
            BlockHeader splitBlock = new BlockHeader(block.offset + alignedSize, remaining);

            splitBlock.prevPhysical = block;
            splitBlock.nextPhysical = block.nextPhysical;
            if (block.nextPhysical != null) {
                block.nextPhysical.prevPhysical = splitBlock;
            }
            block.nextPhysical = splitBlock;

            insertFreeBlock(splitBlock);
        }

        this.activeBytes += block.size;
        return block;
    }

    public synchronized void freeTlsf(BlockHeader block) {
        if (block == null || block.free) return;

        this.activeBytes -= block.size;

        // Immediate coalescing with right physical neighbor
        if (block.nextPhysical != null && block.nextPhysical.free) {
            BlockHeader next = block.nextPhysical;
            removeFreeBlock(next);
            block.size += next.size;
            block.nextPhysical = next.nextPhysical;
            if (next.nextPhysical != null) {
                next.nextPhysical.prevPhysical = block;
            }
        }

        // Immediate coalescing with left physical neighbor
        if (block.prevPhysical != null && block.prevPhysical.free) {
            BlockHeader prev = block.prevPhysical;
            removeFreeBlock(prev);
            prev.size += block.size;
            prev.nextPhysical = block.nextPhysical;
            if (block.nextPhysical != null) {
                block.nextPhysical.prevPhysical = prev;
            }
            block = prev;
        }

        insertFreeBlock(block);
    }
}
