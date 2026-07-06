package com.xeno.client.renderer;

import java.util.ArrayList;
import java.util.List;

public class OffsetAllocator {
    public static class Slot {
        public final long offset;
        public final long size;

        public Slot(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
    }

    private final long totalSize;
    private final List<Slot> freeSlots = new ArrayList<>();
    private long currentEnd = 0;

    public OffsetAllocator(long totalSize) {
        this.totalSize = totalSize;
    }

    public synchronized Slot allocate(long requestedSize, long alignSize) {
        long alignMask = alignSize - 1;

        // 1. Try to find a fitting slot in the free list (First-fit)
        for (int i = 0; i < freeSlots.size(); i++) {
            Slot s = freeSlots.get(i);
            long alignedOffset = (s.offset + alignMask) & ~alignMask;
            long padding = alignedOffset - s.offset;
            if (s.size >= requestedSize + padding) {
                freeSlots.remove(i);

                // Return padding before aligned offset to free list
                if (padding > 0) {
                    freeSlots.add(i, new Slot(s.offset, padding));
                    i++;
                }

                // Return remaining slot space to free list
                long allocatedEnd = alignedOffset + requestedSize;
                long remainingSize = (s.offset + s.size) - allocatedEnd;
                if (remainingSize > 0) {
                    freeSlots.add(i, new Slot(allocatedEnd, remainingSize));
                }

                return new Slot(alignedOffset, requestedSize);
            }
        }

        // 2. Bump allocate from the end
        long alignedOffset = (currentEnd + alignMask) & ~alignMask;
        if (alignedOffset + requestedSize <= totalSize) {
            currentEnd = alignedOffset + requestedSize;
            return new Slot(alignedOffset, requestedSize);
        }

        return null; // Out of memory in this allocator
    }

    public synchronized void free(Slot slot) {
        // Keep free list sorted by offset
        int insertIdx = 0;
        while (insertIdx < freeSlots.size() && freeSlots.get(insertIdx).offset < slot.offset) {
            insertIdx++;
        }
        freeSlots.add(insertIdx, slot);

        // Coalesce / merge adjacent free blocks
        for (int i = 0; i < freeSlots.size() - 1; ) {
            Slot s1 = freeSlots.get(i);
            Slot s2 = freeSlots.get(i + 1);
            if (s1.offset + s1.size == s2.offset) {
                freeSlots.set(i, new Slot(s1.offset, s1.size + s2.size));
                freeSlots.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    public synchronized long totalFreeSpace() {
        long total = 0;
        for (Slot s : freeSlots) {
            total += s.size;
        }
        long bumpFree = totalSize - currentEnd;
        if (bumpFree > 0) total += bumpFree;
        return total;
    }

    public synchronized long largestFreeSlot() {
        long maxSize = totalSize - currentEnd;
        for (Slot s : freeSlots) {
            if (s.size > maxSize) maxSize = s.size;
        }
        return maxSize;
    }

    public synchronized float fragmentation() {
        long freeSpace = totalFreeSpace();
        if (freeSpace <= 0) return 0.0f;
        return 1.0f - (float) largestFreeSlot() / (float) freeSpace;
    }
}
