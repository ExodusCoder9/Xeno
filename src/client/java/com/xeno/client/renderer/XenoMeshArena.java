package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XenoMeshArena implements AutoCloseable {
    public static class Segment {
        public final GpuBuffer vertexBuffer;
        public final GpuBuffer indexBuffer;
        public final OffsetAllocator vertexAllocator;
        public final OffsetAllocator indexAllocator;
        
        public final GpuBufferSlice.MappedView vertexMappedView;
        public final GpuBufferSlice.MappedView indexMappedView;
        public final long vertexBaseAddress;
        public final long indexBaseAddress;
        public int activeAllocations = 0;

        public Segment(GpuDevice device, boolean isIntegrated, long vertexSize, long indexSize) {
            int vertexUsage = GpuBuffer.USAGE_VERTEX;
            int indexUsage = GpuBuffer.USAGE_INDEX;
            if (isIntegrated) {
                vertexUsage |= GpuBuffer.USAGE_MAP_WRITE;
                indexUsage |= GpuBuffer.USAGE_MAP_WRITE;
            } else {
                vertexUsage |= GpuBuffer.USAGE_COPY_DST;
                indexUsage |= GpuBuffer.USAGE_COPY_DST;
            }

            this.vertexBuffer = device.createBuffer(() -> "Xeno Vertex Segment", vertexUsage, vertexSize);
            this.indexBuffer = device.createBuffer(() -> "Xeno Index Segment", indexUsage, indexSize);
            this.vertexAllocator = new OffsetAllocator(vertexSize);
            this.indexAllocator = new OffsetAllocator(indexSize);

            if (isIntegrated) {
                this.vertexMappedView = this.vertexBuffer.map(false, true);
                this.indexMappedView = this.indexBuffer.map(false, true);
                this.vertexBaseAddress = MemorySegment.ofBuffer(this.vertexMappedView.data()).address();
                this.indexBaseAddress = MemorySegment.ofBuffer(this.indexMappedView.data()).address();
            } else {
                this.vertexMappedView = null;
                this.indexMappedView = null;
                this.vertexBaseAddress = 0;
                this.indexBaseAddress = 0;
            }
        }

        public void close() {
            if (vertexMappedView != null) vertexMappedView.close();
            if (indexMappedView != null) indexMappedView.close();
            vertexBuffer.close();
            indexBuffer.close();
        }
    }

    public record Allocation(Segment segment, OffsetAllocator.Slot vertexSlot, OffsetAllocator.Slot indexSlot) {}

    private final GpuDevice device;
    private final boolean isIntegrated;
    private final long defaultVertexCapacity;
    private final long defaultIndexCapacity;
    private final long vertexAlign;
    private final long indexAlign;
    
    private final List<Segment> segments = new ArrayList<>();
    private final Map<SectionMesh, Allocation> allocations = new HashMap<>();

    public XenoMeshArena(GpuDevice device, boolean isIntegrated, long defaultVertexCapacity, long defaultIndexCapacity, long vertexAlign, long indexAlign) {
        this.device = device;
        this.isIntegrated = isIntegrated;
        this.defaultVertexCapacity = defaultVertexCapacity;
        this.defaultIndexCapacity = defaultIndexCapacity;
        this.vertexAlign = vertexAlign;
        this.indexAlign = indexAlign;
        
        // Allocate initial segment
        this.segments.add(new Segment(device, isIntegrated, defaultVertexCapacity, defaultIndexCapacity));
    }

    public boolean isIntegrated() {
        return this.isIntegrated;
    }

    public synchronized Allocation allocate(SectionMesh key, long vertexSize, long indexSize) {
        free(key);

        for (Segment segment : segments) {
            OffsetAllocator.Slot vertexSlot = segment.vertexAllocator.allocate(vertexSize, vertexAlign);
            if (vertexSlot != null) {
                OffsetAllocator.Slot indexSlot = segment.indexAllocator.allocate(indexSize, indexAlign);
                if (indexSlot != null) {
                    segment.activeAllocations++;
                    Allocation alloc = new Allocation(segment, vertexSlot, indexSlot);
                    allocations.put(key, alloc);
                    return alloc;
                } else {
                    segment.vertexAllocator.free(vertexSlot);
                }
            }
        }

        // Segment overflow: Create and append new segment
        Segment newSegment = new Segment(device, isIntegrated, defaultVertexCapacity, defaultIndexCapacity);
        segments.add(newSegment);
        
        OffsetAllocator.Slot vertexSlot = newSegment.vertexAllocator.allocate(vertexSize, vertexAlign);
        OffsetAllocator.Slot indexSlot = newSegment.indexAllocator.allocate(indexSize, indexAlign);
        newSegment.activeAllocations++;

        Allocation alloc = new Allocation(newSegment, vertexSlot, indexSlot);
        allocations.put(key, alloc);
        return alloc;
    }

    public synchronized void free(SectionMesh key) {
        Allocation alloc = allocations.remove(key);
        if (alloc != null) {
            alloc.segment.vertexAllocator.free(alloc.vertexSlot);
            alloc.segment.indexAllocator.free(alloc.indexSlot);
            alloc.segment.activeAllocations--;

            // Garbage collect empty non-primary segments
            if (alloc.segment.activeAllocations == 0 && segments.size() > 1) {
                segments.remove(alloc.segment);
                alloc.segment.close();
            }
        }
    }

    public synchronized SectionRenderDispatcher.RenderSectionBufferSlice getSlice(SectionMesh key) {
        Allocation alloc = allocations.get(key);
        if (alloc == null) return null;
        return new SectionRenderDispatcher.RenderSectionBufferSlice(
            alloc.segment.vertexBuffer, alloc.vertexSlot.offset,
            alloc.segment.indexBuffer, alloc.indexSlot.offset
        );
    }

    @Override
    public synchronized void close() {
        for (Segment segment : segments) {
            segment.close();
        }
        segments.clear();
        allocations.clear();
    }
}
