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

package com.xeno.client.common.render.chunk;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.xeno.client.common.memory.MemoryAccess;

public final class XenoTranslucentQuadSorter {
    private static final int RADIX_BITS = 8;
    private static final int RADIX_SIZE = 1 << RADIX_BITS; // 256
    private static final int RADIX_MASK = RADIX_SIZE - 1;  // 0xFF
    private static final int INSERTION_SORT_THRESHOLD = 32;
    private static final ThreadLocal<ScratchBuffers> SCRATCH = ThreadLocal.withInitial(ScratchBuffers::new);
    private final float[] quadCentroids;
    private final int quadCount;
    private final IndexType indexType;
    public XenoTranslucentQuadSorter(float[] centroids, int quadCount, IndexType indexType) {
        this.quadCentroids = centroids;
        this.quadCount = quadCount;
        this.indexType = indexType;
    }
    public ByteBufferBuilder.Result sortAndBuildIndexBuffer(ByteBufferBuilder target, float camX, float camY, float camZ) {
        final int n = this.quadCount;
        if (n == 0) {
            return null;
        }
        final ScratchBuffers scratch = SCRATCH.get();
        scratch.ensureCapacity(n);
        final int[] sortedQuads = scratch.sortedQuads;
        if (n == 1) {
            sortedQuads[0] = 0;
        } else if (n <= INSERTION_SORT_THRESHOLD) {
            sortSmall(this.quadCentroids, n, camX, camY, camZ, sortedQuads, scratch.distances);
        } else {
            sortRadix32(this.quadCentroids, n, camX, camY, camZ, sortedQuads, scratch);
        }
        final int bytesPerIndex = this.indexType.bytes;
        final long byteSize = (long) n * 6L * bytesPerIndex;
        final long pointer = target.reserve((int) byteSize);
        if (this.indexType == IndexType.SHORT) {
            writeIndicesShort(pointer, sortedQuads, n);
        } else {
            writeIndicesInt(pointer, sortedQuads, n);
        }
        return target.build();
    }

    private static void sortSmall(
            float[] centroids,
            int count,
            float camX, float camY, float camZ,
            int[] outQuads,
            float[] distBuffer
    ) {
        for (int i = 0; i < count; i++) {
            final int cIdx = i * 3;
            final float dx = centroids[cIdx] - camX;
            final float dy = centroids[cIdx + 1] - camY;
            final float dz = centroids[cIdx + 2] - camZ;
            distBuffer[i] = dx * dx + dy * dy + dz * dz;
            outQuads[i] = i;
        }
        for (int i = 1; i < count; i++) {
            final int q = outQuads[i];
            final float d = distBuffer[q];
            int j = i - 1;
            while (j >= 0 && distBuffer[outQuads[j]] < d) {
                outQuads[j + 1] = outQuads[j];
                j--;
            }
            outQuads[j + 1] = q;
        }
    }

    private static void sortRadix32(
            float[] centroids,
            int count,
            float camX, float camY, float camZ,
            int[] outQuads,
            ScratchBuffers scratch
    ) {
        final int[] keysA = scratch.keysA;
        final int[] keysB = scratch.keysB;
        final int[] quadsA = scratch.quadsA;
        final int[] quadsB = scratch.quadsB;
        for (int i = 0; i < count; i++) {
            final int cIdx = i * 3;
            final float dx = centroids[cIdx] - camX;
            final float dy = centroids[cIdx + 1] - camY;
            final float dz = centroids[cIdx + 2] - camZ;
            final float distSq = dx * dx + dy * dy + dz * dz;
            keysA[i] = Float.floatToRawIntBits(distSq);
            quadsA[i] = i;
        }
        radixPassDescending(quadsA, keysA, quadsB, keysB, count, 0, scratch.counts, scratch.offsets);
        radixPassDescending(quadsB, keysB, quadsA, keysA, count, 8, scratch.counts, scratch.offsets);
        radixPassDescending(quadsA, keysA, quadsB, keysB, count, 16, scratch.counts, scratch.offsets);
        radixPassDescending(quadsB, keysB, outQuads, keysA, count, 24, scratch.counts, scratch.offsets);
    }

    private static void radixPassDescending(
            int[] srcQuads, int[] srcKeys,
            int[] dstQuads, int[] dstKeys,
            int count, int shift,
            int[] counts, int[] offsets
    ) {
        for (int i = 0; i < RADIX_SIZE; i++) {
            counts[i] = 0;
        }
        for (int i = 0; i < count; i++) {
            final int bucket = (srcKeys[i] >>> shift) & RADIX_MASK;
            counts[bucket]++;
        }
        int sum = 0;
        for (int i = RADIX_SIZE - 1; i >= 0; i--) {
            offsets[i] = sum;
            sum += counts[i];
        }

        for (int i = 0; i < count; i++) {
            final int key = srcKeys[i];
            final int bucket = (key >>> shift) & RADIX_MASK;
            final int dest = offsets[bucket]++;
            dstQuads[dest] = srcQuads[i];
            dstKeys[dest] = key;
        }
    }

    private static void writeIndicesShort(long ptr, int[] sortedQuads, int count) {
        for (int i = 0; i < count; i++) {
            final int quad = sortedQuads[i];
            final int base = quad * 4;
            final short b0 = (short) base;
            final short b1 = (short) (base + 1);
            final short b2 = (short) (base + 2);
            final short b3 = (short) (base + 3);

            final long quadPtr = ptr + (long) i * 12L;
            MemoryAccess.putLong(quadPtr, MemoryAccess.packShorts(b0, b1, b2, b2));
            MemoryAccess.putInt(quadPtr + 8L, MemoryAccess.packShorts(b3, b0));
        }
    }

    private static void writeIndicesInt(long ptr, int[] sortedQuads, int count) {
        for (int i = 0; i < count; i++) {
            final int quad = sortedQuads[i];
            final int base = quad * 4;
            final int b1 = base + 1;
            final int b2 = base + 2;
            final int b3 = base + 3;

            final long quadPtr = ptr + (long) i * 24L;
            MemoryAccess.putLong(quadPtr, MemoryAccess.packInts(base, b1));
            MemoryAccess.putLong(quadPtr + 8L, MemoryAccess.packInts(b2, b2));
            MemoryAccess.putLong(quadPtr + 16L, MemoryAccess.packInts(b3, base));
        }
    }

    private static final class ScratchBuffers {
        int[] sortedQuads = new int[256];
        float[] distances = new float[256];
        int[] keysA = new int[256];
        int[] keysB = new int[256];
        int[] quadsA = new int[256];
        int[] quadsB = new int[256];
        final int[] counts = new int[RADIX_SIZE];
        final int[] offsets = new int[RADIX_SIZE];

        void ensureCapacity(int capacity) {
            if (this.sortedQuads.length < capacity) {
                int newCap = Math.max(capacity, this.sortedQuads.length * 2);
                this.sortedQuads = new int[newCap];
                this.distances = new float[newCap];
                this.keysA = new int[newCap];
                this.keysB = new int[newCap];
                this.quadsA = new int[newCap];
                this.quadsB = new int[newCap];
            }
        }
    }
}
