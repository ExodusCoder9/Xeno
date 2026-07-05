package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages contiguous blocks of GPU memory for terrain meshes.
 * Includes a deferred garbage collection pipeline to prevent Vulkan write-after-free corruption.
 */
public class XenoMeshArena implements AutoCloseable {

    public static class ArenaSegment {
        public final GpuBuffer buffer;
        public final OffsetAllocator allocator;
        public final GpuBufferSlice.MappedView mappedView;
        public final long baseAddress;
        public int activeAllocations = 0;

        public ArenaSegment(GpuDevice device, String name, int usage, boolean isIntegrated, long capacity) {
            if (isIntegrated) {
                usage |= GpuBuffer.USAGE_MAP_WRITE;
            } else {
                usage |= GpuBuffer.USAGE_COPY_DST;
            }

            this.buffer = device.createBuffer(() -> name, usage, capacity);
            this.allocator = new OffsetAllocator(capacity);

            if (isIntegrated) {
                this.mappedView = this.buffer.map(false, true);
                this.baseAddress = java.lang.foreign.MemorySegment.ofBuffer(this.mappedView.data()).address();
            } else {
                this.mappedView = null;
                this.baseAddress = 0;
            }
        }

        public void close() {
            if (this.mappedView != null) {
                this.mappedView.close();
            }
            this.buffer.close();
        }
    }

    public record Allocation(ArenaSegment segment, OffsetAllocator.Slot slot) {}

    private final GpuDevice device;
    private final boolean isIntegrated;
    private final long defaultVertexCapacity;
    private final long defaultIndexCapacity;
    private final long vertexAlign;
    private final long indexAlign;

    private final List<ArenaSegment> vertexSegments = new ArrayList<>();
    private final List<ArenaSegment> indexSegments = new ArrayList<>();
    private final Map<SectionMesh, Allocation> vertexAllocations = new HashMap<>();
    private final Map<SectionMesh, Allocation> indexAllocations = new HashMap<>();

    private final List<List<Runnable>> deferredFrees = new ArrayList<>();

    public XenoMeshArena(GpuDevice device, boolean isIntegrated, long defaultVertexCapacity, long defaultIndexCapacity, long vertexAlign, long indexAlign) {
        this.device = device;
        this.isIntegrated = isIntegrated;
        this.defaultVertexCapacity = defaultVertexCapacity;
        this.defaultIndexCapacity = defaultIndexCapacity;
        this.vertexAlign = vertexAlign;
        this.indexAlign = indexAlign;

        for (int i = 0; i < 3; i++) {
            this.deferredFrees.add(new ArrayList<>());
        }

        this.vertexSegments.add(new ArenaSegment(device, "Xeno Vertex Segment", GpuBuffer.USAGE_VERTEX, isIntegrated, defaultVertexCapacity));
        this.indexSegments.add(new ArenaSegment(device, "Xeno Index Segment", GpuBuffer.USAGE_INDEX, isIntegrated, defaultIndexCapacity));
    }

    public boolean isIntegrated() {
        return this.isIntegrated;
    }

    /**
     * Shifts the deferred free queue. Executed once per frame by the Render Thread.
     */
    public synchronized void tickFrees() {
        List<Runnable> readyToFree = this.deferredFrees.remove(0);
        for (Runnable freeAction : readyToFree) {
            freeAction.run();
        }
        readyToFree.clear();
        this.deferredFrees.add(readyToFree);
    }

    private void queueFree(Runnable freeAction) {
        this.deferredFrees.get(this.deferredFrees.size() - 1).add(freeAction);
    }

    private Allocation allocate(SectionMesh key, long size, long align, List<ArenaSegment> segments, Map<SectionMesh, Allocation> allocations, String name, int usage, long defaultCapacity) {
        if (size <= 0) return null;

        for (ArenaSegment segment : segments) {
            OffsetAllocator.Slot slot = segment.allocator.allocate(size, align);
            if (slot != null) {
                segment.activeAllocations++;
                Allocation alloc = new Allocation(segment, slot);
                allocations.put(key, alloc);
                return alloc;
            }
        }

        ArenaSegment newSegment = new ArenaSegment(device, name, usage, isIntegrated, defaultCapacity);
        segments.add(newSegment);

        OffsetAllocator.Slot slot = newSegment.allocator.allocate(size, align);
        newSegment.activeAllocations++;

        Allocation alloc = new Allocation(newSegment, slot);
        allocations.put(key, alloc);
        return alloc;
    }

    public synchronized Allocation allocateVertex(SectionMesh key, long size) {
        this.freeVertex(key);
        return allocate(key, size, vertexAlign, vertexSegments, vertexAllocations, "Xeno Vertex Segment", GpuBuffer.USAGE_VERTEX, defaultVertexCapacity);
    }

    public synchronized Allocation allocateIndex(SectionMesh key, long size) {
        this.freeIndex(key);
        return allocate(key, size, indexAlign, indexSegments, indexAllocations, "Xeno Index Segment", GpuBuffer.USAGE_INDEX, defaultIndexCapacity);
    }

    public synchronized void freeVertex(SectionMesh key) {
        Allocation alloc = this.vertexAllocations.remove(key);
        if (alloc != null) {
            queueFree(() -> {
                alloc.segment.allocator.free(alloc.slot);
                alloc.segment.activeAllocations--;
                if (alloc.segment.activeAllocations == 0 && this.vertexSegments.size() > 1) {
                    this.vertexSegments.remove(alloc.segment);
                    alloc.segment.close();
                }
            });
        }
    }

    public synchronized void freeIndex(SectionMesh key) {
        Allocation alloc = this.indexAllocations.remove(key);
        if (alloc != null) {
            queueFree(() -> {
                alloc.segment.allocator.free(alloc.slot);
                alloc.segment.activeAllocations--;
                if (alloc.segment.activeAllocations == 0 && this.indexSegments.size() > 1) {
                    this.indexSegments.remove(alloc.segment);
                    alloc.segment.close();
                }
            });
        }
    }

    public synchronized void freeAll(SectionMesh key) {
        this.freeVertex(key);
        this.freeIndex(key);
    }

    public synchronized SectionRenderDispatcher.RenderSectionBufferSlice getSlice(SectionMesh key) {
        Allocation vAlloc = this.vertexAllocations.get(key);
        if (vAlloc == null) return null;

        Allocation iAlloc = this.indexAllocations.get(key);
        GpuBuffer indexBuffer = iAlloc != null ? iAlloc.segment.buffer : null;
        long indexBufferOffset = iAlloc != null ? iAlloc.slot.offset : 0L;

        return new SectionRenderDispatcher.RenderSectionBufferSlice(
                vAlloc.segment.buffer, vAlloc.slot.offset,
                indexBuffer, indexBufferOffset
        );
    }

    @Override
    public synchronized void close() {
        for (List<Runnable> freeList : this.deferredFrees) {
            for (Runnable freeAction : freeList) freeAction.run();
        }
        this.deferredFrees.clear();

        for (ArenaSegment segment : this.vertexSegments) segment.close();
        this.vertexSegments.clear();
        this.vertexAllocations.clear();

        for (ArenaSegment segment : this.indexSegments) segment.close();
        this.indexSegments.clear();
        this.indexAllocations.clear();
    }
}