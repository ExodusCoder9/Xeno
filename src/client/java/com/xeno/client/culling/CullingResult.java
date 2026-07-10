package com.xeno.client.culling;

public final class CullingResult {
    public static final byte OCCLUDED = 0;
    public static final byte MAYBE = 1;
    public static final byte SURELY_VISIBLE = 2;

    private byte[] readBuffer;
    private byte[] writeBuffer;
    private final byte[] prevVisibility;
    private long version;
    private int totalSections;
    private long cameraSectionNode;

    public CullingResult(int maxSections) {
        this.readBuffer = new byte[maxSections];
        this.writeBuffer = new byte[maxSections];
        this.prevVisibility = new byte[maxSections];
        this.version = 0;
        this.totalSections = 0;
        this.cameraSectionNode = 0;
    }

    public void init(int totalSections, long cameraSectionNode) {
        this.totalSections = totalSections;
        this.cameraSectionNode = cameraSectionNode;
        if (prevVisibility[0] != 0 || totalSections <= prevVisibility.length) {
            System.arraycopy(prevVisibility, 0, writeBuffer, 0, totalSections);
            for (int i = 0; i < totalSections; i++) {
                if (writeBuffer[i] == SURELY_VISIBLE) {
                    writeBuffer[i] = MAYBE;
                }
            }
        } else {
            java.util.Arrays.fill(writeBuffer, 0, totalSections, MAYBE);
        }
    }

    public byte getWriteVisibility(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= totalSections) return OCCLUDED;
        return writeBuffer[sectionIndex];
    }

    public void markSurelyVisible(int sectionIndex) {
        if (sectionIndex >= 0 && sectionIndex < totalSections && writeBuffer[sectionIndex] != SURELY_VISIBLE) {
            writeBuffer[sectionIndex] = SURELY_VISIBLE;
        }
    }

    public void markOccluded(int sectionIndex) {
        if (sectionIndex >= 0 && sectionIndex < totalSections && writeBuffer[sectionIndex] != OCCLUDED) {
            writeBuffer[sectionIndex] = OCCLUDED;
        }
    }

    public boolean wasPreviouslyVisible(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= totalSections) return false;
        return prevVisibility[sectionIndex] == SURELY_VISIBLE;
    }

    public void publish(long cameraSectionNode) {
        this.cameraSectionNode = cameraSectionNode;
        System.arraycopy(writeBuffer, 0, prevVisibility, 0, totalSections);
        byte[] temp = readBuffer;
        readBuffer = writeBuffer;
        writeBuffer = temp;
        this.version++;
    }

    public byte[] visibilityArray() {
        return readBuffer;
    }
}
