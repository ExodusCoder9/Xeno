package com.xeno.client.culling;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.Direction;

public class CullNode {
    public SectionRenderDispatcher.RenderSection section;
    public byte sourceDirections;
    public byte directions;
    public int step;

    public CullNode(SectionRenderDispatcher.RenderSection section, Direction sourceDirection, int step) {
        this.reset(section, sourceDirection, step);
    }

    public void reset(SectionRenderDispatcher.RenderSection section, Direction sourceDirection, int step) {
        this.section = section;
        this.sourceDirections = 0;
        this.directions = 0;
        if (sourceDirection != null) {
            this.addSourceDirection(sourceDirection);
        }
        this.step = step;
    }

    public void setDirections(byte oldDirections, Direction direction) {
        this.directions = (byte)(this.directions | oldDirections | 1 << direction.ordinal());
    }

    public boolean hasDirection(Direction direction) {
        return (this.directions & (1 << direction.ordinal())) > 0;
    }

    public void addSourceDirection(Direction direction) {
        this.sourceDirections = (byte)(this.sourceDirections | 1 << direction.ordinal());
    }

    public boolean hasSourceDirection(int directionOrdinal) {
        return (this.sourceDirections & (1 << directionOrdinal)) > 0;
    }

    public boolean hasSourceDirections() {
        return this.sourceDirections != 0;
    }
}
