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
 */
public class XenoMeshArena implements AutoCloseable {

    public static class VertexSegment {
        public final GpuBuffer buffer;
        public final OffsetAllocator allocator;
        public final GpuBufferSlice.MappedView mappedView;
        public final long baseAddress;
        public int activeAllocations = 0;

        public VertexSegment(GpuDevice device, boolean isIntegrated, long capacity) {
            int usage = GpuBuffer.USAGE_VERTEX;
            if (isIntegrated) {
                usage |= GpuBuffer.USAGE_MAP_WRITE;
            } else {
                usage |= GpuBuffer.USAGE_COPY_DST;
            }

            this.buffer = device.createBuffer(() -> "Xeno Vertex Segment", usage, capacity);
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

    public static class IndexSegment {
        public final GpuBuffer buffer;
        public final OffsetAllocator allocator;
        public final GpuBufferSlice.MappedView mappedView;
        public final long baseAddress;
        public int activeAllocations = 0;

        public IndexSegment(GpuDevice device, boolean isIntegrated, long capacity) {
            int usage = GpuBuffer.USAGE_INDEX;
            if (isIntegrated) {
                usage |= GpuBuffer.USAGE_MAP_WRITE;
            } else {
                usage |= GpuBuffer.USAGE_COPY_DST;
            }

            this.buffer = device.createBuffer(() -> "Xeno Index Segment", usage, capacity);
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

    public record VertexAllocation(VertexSegment segment, OffsetAllocator.Slot slot) {}
    public record IndexAllocation(IndexSegment segment, OffsetAllocator.Slot slot) {}

    private final GpuDevice device;
    private final boolean isIntegrated;
    private final long defaultVertexCapacity;
    private final long defaultIndexCapacity;
    private final long vertexAlign;
    private final long indexAlign;

    private final List<VertexSegment> vertexSegments = new ArrayList<>();
    private final List<IndexSegment> indexSegments = new ArrayList<>();
    private final Map<SectionMesh, VertexAllocation> vertexAllocations = new HashMap<>();
    private final Map<SectionMesh, IndexAllocation> indexAllocations = new HashMap<>();

    public XenoMeshArena(GpuDevice device, boolean isIntegrated, long defaultVertexCapacity, long defaultIndexCapacity, long vertexAlign, long indexAlign) {
        this.device = device;
        this.isIntegrated = isIntegrated;
        this.defaultVertexCapacity = defaultVertexCapacity;
        this.defaultIndexCapacity = defaultIndexCapacity;
        this.vertexAlign = vertexAlign;
        this.indexAlign = indexAlign;

        this.vertexSegments.add(new VertexSegment(device, isIntegrated, defaultVertexCapacity));
        this.indexSegments.add(new IndexSegment(device, isIntegrated, defaultIndexCapacity));
    }

    public boolean isIntegrated() {
        return this.isIntegrated;
    }

    public synchronized VertexAllocation allocateVertex(SectionMesh key, long size) {
        this.freeVertex(key);
        if (size <= 0) return null;

        for (VertexSegment segment : this.vertexSegments) {
            OffsetAllocator.Slot slot = segment.allocator.allocate(size, this.vertexAlign);
            if (slot != null) {
                segment.activeAllocations++;
                VertexAllocation alloc = new VertexAllocation(segment, slot);
                this.vertexAllocations.put(key, alloc);
                return alloc;
            }
        }

        VertexSegment newSegment = new VertexSegment(this.device, this.isIntegrated, this.defaultVertexCapacity);
        this.vertexSegments.add(newSegment);

        OffsetAllocator.Slot slot = newSegment.allocator.allocate(size, this.vertexAlign);
        newSegment.activeAllocations++;

        VertexAllocation alloc = new VertexAllocation(newSegment, slot);
        this.vertexAllocations.put(key, alloc);
        return alloc;
    }

    public synchronized IndexAllocation allocateIndex(SectionMesh key, long size) {
        this.freeIndex(key);
        if (size <= 0) return null;

        for (IndexSegment segment : this.indexSegments) {
            OffsetAllocator.Slot slot = segment.allocator.allocate(size, this.indexAlign);
            if (slot != null) {
                segment.activeAllocations++;
                IndexAllocation alloc = new IndexAllocation(segment, slot);
                this.indexAllocations.put(key, alloc);
                return alloc;
            }
        }

        IndexSegment newSegment = new IndexSegment(this.device, this.isIntegrated, this.defaultIndexCapacity);
        this.indexSegments.add(newSegment);

        OffsetAllocator.Slot slot = newSegment.allocator.allocate(size, this.indexAlign);
        newSegment.activeAllocations++;

        IndexAllocation alloc = new IndexAllocation(newSegment, slot);
        this.indexAllocations.put(key, alloc);
        return alloc;
    }

    public synchronized void freeVertex(SectionMesh key) {
        VertexAllocation alloc = this.vertexAllocations.remove(key);
        if (alloc != null) {
            alloc.segment.allocator.free(alloc.slot);
            alloc.segment.activeAllocations--;

            if (alloc.segment.activeAllocations == 0 && this.vertexSegments.size() > 1) {
                this.vertexSegments.remove(alloc.segment);
                alloc.segment.close();
            }
        }
    }

    public synchronized void freeIndex(SectionMesh key) {
        IndexAllocation alloc = this.indexAllocations.remove(key);
        if (alloc != null) {
            alloc.segment.allocator.free(alloc.slot);
            alloc.segment.activeAllocations--;

            if (alloc.segment.activeAllocations == 0 && this.indexSegments.size() > 1) {
                this.indexSegments.remove(alloc.segment);
                alloc.segment.close();
            }
        }
    }

    public synchronized void free(SectionMesh key) {
        this.freeVertex(key);
        this.freeIndex(key);
    }

    public synchronized SectionRenderDispatcher.RenderSectionBufferSlice getSlice(SectionMesh key) {
        VertexAllocation vAlloc = this.vertexAllocations.get(key);
        if (vAlloc == null) return null;

        IndexAllocation iAlloc = this.indexAllocations.get(key);
        GpuBuffer indexBuffer = iAlloc != null ? iAlloc.segment.buffer : null;
        long indexBufferOffset = iAlloc != null ? iAlloc.slot.offset : 0L;

        return new SectionRenderDispatcher.RenderSectionBufferSlice(
                vAlloc.segment.buffer, vAlloc.slot.offset,
                indexBuffer, indexBufferOffset
        );
    }

    @Override
    public synchronized void close() {
        for (VertexSegment segment : this.vertexSegments) {
            segment.close();
        }
        this.vertexSegments.clear();
        this.vertexAllocations.clear();

        for (IndexSegment segment : this.indexSegments) {
            segment.close();
        }
        this.indexSegments.clear();
        this.indexAllocations.clear();
    }
}