package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.IndexType;

public class XenoDrawKey {
    public GpuBuffer vertexBuffer;
    public GpuBuffer indexBuffer;
    public IndexType indexType;
    public int firstIndex;
    public int indexCount;
    public int baseVertex;

    public XenoDrawKey() {}

    public XenoDrawKey(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int firstIndex, int indexCount, int baseVertex) {
        set(vertexBuffer, indexBuffer, indexType, firstIndex, indexCount, baseVertex);
    }

    public void set(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int firstIndex, int indexCount, int baseVertex) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexType = indexType;
        this.firstIndex = firstIndex;
        this.indexCount = indexCount;
        this.baseVertex = baseVertex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XenoDrawKey)) return false;
        XenoDrawKey that = (XenoDrawKey) o;
        return firstIndex == that.firstIndex &&
               indexCount == that.indexCount &&
               baseVertex == that.baseVertex &&
               vertexBuffer == that.vertexBuffer &&
               indexBuffer == that.indexBuffer &&
               indexType == that.indexType;
    }

    @Override
    public int hashCode() {
        int result = vertexBuffer != null ? vertexBuffer.hashCode() : 0;
        result = 31 * result + (indexBuffer != null ? indexBuffer.hashCode() : 0);
        result = 31 * result + (indexType != null ? indexType.hashCode() : 0);
        result = 31 * result + firstIndex;
        result = 31 * result + indexCount;
        result = 31 * result + baseVertex;
        return result;
    }
}
