package com.xeno.client.culling;

import net.minecraft.core.Direction;

public final class XenoNode {
    public static final int UNSET = -1;

    public long sectionNode;
    public int sectionIndex;
    public byte sourceDirections;
    public byte propagatedDirs;
    public int step;

    public void reset(long sectionNode, int sectionIndex, int step) {
        this.sectionNode = sectionNode;
        this.sectionIndex = sectionIndex;
        this.sourceDirections = 0;
        this.propagatedDirs = 0;
        this.step = step;
    }

    public void addSourceDir(Direction dir) {
        sourceDirections |= (byte) (1 << dir.ordinal());
    }

    public void markPropagated(Direction dir) {
        propagatedDirs |= (byte) (1 << dir.ordinal());
    }

    public boolean hasSourceDir(int ordinal) {
        return (sourceDirections & (1 << ordinal)) != 0;
    }

    public boolean hasAnySourceDir() {
        return sourceDirections != 0;
    }

    public boolean hasPropagated(Direction dir) {
        return (propagatedDirs & (1 << dir.ordinal())) != 0;
    }
}
