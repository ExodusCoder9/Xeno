package com.xeno.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.IndexType;

public class XenoDrawList {
    public int count;
    public GpuBuffer[] vertexBuffers;
    public GpuBuffer[] indexBuffers;
    public IndexType[] indexTypes;
    public int[] firstIndices;
    public int[] indexCounts;
    public int[] baseVertices;
    public int[] uboIndices;

    public XenoDrawList(int capacity) {
        this.vertexBuffers = new GpuBuffer[capacity];
        this.indexBuffers = new GpuBuffer[capacity];
        this.indexTypes = new IndexType[capacity];
        this.firstIndices = new int[capacity];
        this.indexCounts = new int[capacity];
        this.baseVertices = new int[capacity];
        this.uboIndices = new int[capacity];
    }

    public void clear() {
        this.count = 0;
    }

    public void add(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int firstIndex, int indexCount, int baseVertex, int uboIndex) {
        if (count >= vertexBuffers.length) {
            grow();
        }
        vertexBuffers[count] = vertexBuffer;
        indexBuffers[count] = indexBuffer;
        indexTypes[count] = indexType;
        firstIndices[count] = firstIndex;
        indexCounts[count] = indexCount;
        baseVertices[count] = baseVertex;
        uboIndices[count] = uboIndex;
        count++;
    }

    private void grow() {
        int newCap = vertexBuffers.length * 2;
        GpuBuffer[] newVb = new GpuBuffer[newCap];
        GpuBuffer[] newIb = new GpuBuffer[newCap];
        IndexType[] newIt = new IndexType[newCap];
        int[] newFi = new int[newCap];
        int[] newIc = new int[newCap];
        int[] newBv = new int[newCap];
        int[] newUi = new int[newCap];

        System.arraycopy(vertexBuffers, 0, newVb, 0, count);
        System.arraycopy(indexBuffers, 0, newIb, 0, count);
        System.arraycopy(indexTypes, 0, newIt, 0, count);
        System.arraycopy(firstIndices, 0, newFi, 0, count);
        System.arraycopy(indexCounts, 0, newIc, 0, count);
        System.arraycopy(baseVertices, 0, newBv, 0, count);
        System.arraycopy(uboIndices, 0, newUi, 0, count);

        vertexBuffers = newVb;
        indexBuffers = newIb;
        indexTypes = newIt;
        firstIndices = newFi;
        indexCounts = newIc;
        baseVertices = newBv;
        uboIndices = newUi;
    }
}
