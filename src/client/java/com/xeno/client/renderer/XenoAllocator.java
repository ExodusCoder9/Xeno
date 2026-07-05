package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.lang.foreign.MemorySegment;

public class XenoAllocator {
    private static final long VERTEX_CAPACITY = 256 * 1024 * 1024L; // 256MB
    private static final long INDEX_CAPACITY = 128 * 1024 * 1024L;  // 128MB

    private static GpuBuffer vertexBuffer;
    private static GpuBuffer indexBuffer;

    public static final Map<Object, Allocation> vertexAllocations = new IdentityHashMap<>();
    public static final Map<Object, Allocation> indexAllocations = new IdentityHashMap<>();

    public static class Allocation {
        public final Object key;
        public long offset;
        public long size;

        public Allocation(Object key, long offset, long size) {
            this.key = key;
            this.offset = offset;
            this.size = size;
        }
    }

    private static final LinkedList<FreeBlock> vertexFreeList = new LinkedList<>();
    private static final LinkedList<FreeBlock> indexFreeList = new LinkedList<>();

    private static class FreeBlock implements Comparable<FreeBlock> {
        long offset;
        long size;

        FreeBlock(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }

        @Override
        public int compareTo(FreeBlock o) {
            return Long.compare(this.offset, o.offset);
        }
    }

    public static void init() {
        if (vertexBuffer == null || vertexBuffer.isClosed()) {
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Xeno Unified Vertex Buffer",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_VERTEX,
                VERTEX_CAPACITY
            );
            vertexFreeList.clear();
            vertexFreeList.add(new FreeBlock(0, VERTEX_CAPACITY));
        }
        if (indexBuffer == null || indexBuffer.isClosed()) {
            indexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Xeno Unified Index Buffer",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_INDEX,
                INDEX_CAPACITY
            );
            indexFreeList.clear();
            indexFreeList.add(new FreeBlock(0, INDEX_CAPACITY));
        }
    }

    public static GpuBuffer getVertexBuffer() {
        init();
        return vertexBuffer;
    }

    public static GpuBuffer getIndexBuffer() {
        init();
        return indexBuffer;
    }

    private static long allocate(LinkedList<FreeBlock> freeList, long size, long alignment) {
        long alignedSize = (size + alignment - 1) & ~(alignment - 1);
        for (int i = 0; i < freeList.size(); i++) {
            FreeBlock block = freeList.get(i);
            long alignedOffset = (block.offset + alignment - 1) & ~(alignment - 1);
            long unusedBefore = alignedOffset - block.offset;
            if (block.size >= alignedSize + unusedBefore) {
                freeList.remove(i);
                if (unusedBefore > 0) {
                    freeList.add(i, new FreeBlock(block.offset, unusedBefore));
                    i++;
                }
                long remainingSize = block.size - alignedSize - unusedBefore;
                if (remainingSize > 0) {
                    freeList.add(i, new FreeBlock(alignedOffset + alignedSize, remainingSize));
                }
                return alignedOffset;
            }
        }
        return -1;
    }

    public static synchronized long uploadVertex(Object key, ByteBuffer vertices) {
        init();
        freeVertex(key);

        long size = vertices.remaining();
        long offset = allocate(vertexFreeList, size, 16);
        if (offset == -1) {
            defragment(vertexAllocations, vertexFreeList, vertexBuffer, VERTEX_CAPACITY);
            offset = allocate(vertexFreeList, size, 16);
            if (offset == -1) {
                clearVertex();
                offset = allocate(vertexFreeList, size, 16);
            }
        }

        if (offset != -1) {
            GpuBufferSlice slice = vertexBuffer.slice(offset, size);
            try (GpuBufferSlice.MappedView view = slice.map(false, true)) {
                ByteBuffer target = view.data().order(ByteOrder.nativeOrder());
                int prevPos = vertices.position();
                target.put(vertices);
                vertices.position(prevPos); // Restore original position
            }
            vertexAllocations.put(key, new Allocation(key, offset, size));
        }
        return offset;
    }

    public static synchronized long uploadIndex(Object key, ByteBuffer indices) {
        init();
        freeIndex(key);

        long size = indices.remaining();
        long offset = allocate(indexFreeList, size, 16);
        if (offset == -1) {
            defragment(indexAllocations, indexFreeList, indexBuffer, INDEX_CAPACITY);
            offset = allocate(indexFreeList, size, 16);
            if (offset == -1) {
                clearIndex();
                offset = allocate(indexFreeList, size, 16);
            }
        }

        if (offset != -1) {
            GpuBufferSlice slice = indexBuffer.slice(offset, size);
            try (GpuBufferSlice.MappedView view = slice.map(false, true)) {
                ByteBuffer target = view.data().order(ByteOrder.nativeOrder());
                int prevPos = indices.position();
                target.put(indices);
                indices.position(prevPos); // Restore original position
            }
            indexAllocations.put(key, new Allocation(key, offset, size));
        }
        return offset;
    }

    public static synchronized void freeVertex(Object key) {
        Allocation alloc = vertexAllocations.remove(key);
        if (alloc != null) {
            freeBlock(vertexFreeList, alloc.offset, alloc.size);
        }
    }

    public static synchronized void freeIndex(Object key) {
        Allocation alloc = indexAllocations.remove(key);
        if (alloc != null) {
            freeBlock(indexFreeList, alloc.offset, alloc.size);
        }
    }

    private static void freeBlock(LinkedList<FreeBlock> freeList, long offset, long size) {
        if (offset == -1) return;
        FreeBlock newFree = new FreeBlock(offset, size);
        int insertPos = Collections.binarySearch(freeList, newFree);
        if (insertPos < 0) {
            insertPos = -insertPos - 1;
        }
        freeList.add(insertPos, newFree);

        for (int i = 0; i < freeList.size() - 1; i++) {
            FreeBlock b1 = freeList.get(i);
            FreeBlock b2 = freeList.get(i + 1);
            if (b1.offset + b1.size >= b2.offset) {
                b1.size = Math.max(b1.size, b2.offset + b2.size - b1.offset);
                freeList.remove(i + 1);
                i--;
            }
        }
    }

    private static void defragment(Map<Object, Allocation> allocations, LinkedList<FreeBlock> freeList, GpuBuffer buffer, long capacity) {
        List<Allocation> active = new ArrayList<>(allocations.values());
        active.sort(Comparator.comparingLong(a -> a.offset));

        long nextFreeOffset = 0;
        GpuBufferSlice fullSlice = buffer.slice(0, capacity);
        try (GpuBufferSlice.MappedView view = fullSlice.map(true, true)) {
            ByteBuffer fullBuffer = view.data().order(ByteOrder.nativeOrder());
            MemorySegment segment = MemorySegment.ofBuffer(fullBuffer);

            for (Allocation alloc : active) {
                if (alloc.offset == -1) continue;
                long size = alloc.size;
                if (alloc.offset > nextFreeOffset) {
                    MemorySegment.copy(segment, alloc.offset, segment, nextFreeOffset, size);
                    alloc.offset = nextFreeOffset;
                    
                    com.mojang.blaze3d.vertex.TlsfAllocator.Allocation tlsfAlloc = XenoAllocationTracker.getAllocation(alloc.key, buffer == getVertexBuffer());
                    if (tlsfAlloc != null) {
                        ((com.xeno.client.mixin.AllocationMixinAccessor) tlsfAlloc).xeno_setOffset(nextFreeOffset);
                    }
                }
                nextFreeOffset += (size + 15) & ~15;
            }
        }

        freeList.clear();
        if (nextFreeOffset < capacity) {
            freeList.add(new FreeBlock(nextFreeOffset, capacity - nextFreeOffset));
        }
    }

    private static void clearVertex() {
        vertexAllocations.clear();
        vertexFreeList.clear();
        vertexFreeList.add(new FreeBlock(0, VERTEX_CAPACITY));
    }

    private static void clearIndex() {
        indexAllocations.clear();
        indexFreeList.clear();
        indexFreeList.add(new FreeBlock(0, INDEX_CAPACITY));
    }
}
