package com.xeno.client.renderer.meshing;

import net.minecraft.core.Direction;

public final class SectionFaceData {
    public static final int DIRECTION_COUNT = Direction.values().length;
    public static final int VERTICES_PER_QUAD = 4;

    private int[] faceCounts;
    private int[] vertexCounts;
    private int capacity;

    public SectionFaceData(int initialCapacity) {
        this.capacity = initialCapacity;
        this.faceCounts = new int[initialCapacity * DIRECTION_COUNT];
        this.vertexCounts = new int[initialCapacity];
    }

    public void ensureCapacity(int size) {
        if (size <= capacity) return;
        int newCapacity = Math.max(size, capacity * 2);
        int[] newFaceCounts = new int[newCapacity * DIRECTION_COUNT];
        int[] newVertexCounts = new int[newCapacity];
        System.arraycopy(this.faceCounts, 0, newFaceCounts, 0, this.faceCounts.length);
        System.arraycopy(this.vertexCounts, 0, newVertexCounts, 0, this.vertexCounts.length);
        this.faceCounts = newFaceCounts;
        this.vertexCounts = newVertexCounts;
        this.capacity = newCapacity;
    }

    public void record(int sectionIndex, int[] perDirFaceCounts, int totalVertices) {
        ensureCapacity(sectionIndex + 1);
        int base = sectionIndex * DIRECTION_COUNT;
        System.arraycopy(perDirFaceCounts, 0, this.faceCounts, base, DIRECTION_COUNT);
        this.vertexCounts[sectionIndex] = totalVertices;
    }

    @SuppressWarnings("unused")
    public void recordEmpty(int sectionIndex) {
        ensureCapacity(sectionIndex + 1);
        int base = sectionIndex * DIRECTION_COUNT;
        for (int i = 0; i < DIRECTION_COUNT; i++) {
            this.faceCounts[base + i] = 0;
        }
        this.vertexCounts[sectionIndex] = 0;
    }

    @SuppressWarnings("unused")
    public int getFaceCount(int sectionIndex, int direction) {
        if (sectionIndex >= capacity) return 0;
        return this.faceCounts[sectionIndex * DIRECTION_COUNT + direction];
    }

    public int getVertexCount(int sectionIndex) {
        if (sectionIndex >= capacity) return 0;
        return this.vertexCounts[sectionIndex];
    }

    public int calculateFacesAway(int sectionIndex, float dirX, float dirY, float dirZ) {
        if (sectionIndex >= capacity) return 0;
        int total = 0;
        int base = sectionIndex * DIRECTION_COUNT;

        // Optimized: Unrolled direction dot product loop to eliminate loop overhead, switch statement, and function calls
        // 0: DOWN (dot = dirY)
        if (dirY < -0.2f) total += this.faceCounts[base];
        // 1: UP (dot = -dirY)
        if (-dirY < -0.2f) total += this.faceCounts[base + 1];
        // 2: NORTH (dot = dirZ)
        if (dirZ < -0.2f) total += this.faceCounts[base + 2];
        // 3: SOUTH (dot = -dirZ)
        if (-dirZ < -0.2f) total += this.faceCounts[base + 3];
        // 4: WEST (dot = dirX)
        if (dirX < -0.2f) total += this.faceCounts[base + 4];
        // 5: EAST (dot = -dirX)
        if (-dirX < -0.2f) total += this.faceCounts[base + 5];

        return total;
    }

    @SuppressWarnings("unused")
    public int capacity() {
        return this.capacity;
    }
}
