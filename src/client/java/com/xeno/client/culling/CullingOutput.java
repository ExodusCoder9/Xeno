package com.xeno.client.culling;

import java.util.BitSet;
import java.util.List;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public record CullingOutput(
    List<SectionRenderDispatcher.RenderSection> visibleSections,
    List<SectionRenderDispatcher.RenderSection> solidSections,
    List<SectionRenderDispatcher.RenderSection> cutoutSections,
    List<SectionRenderDispatcher.RenderSection> translucentSections,
    List<SectionRenderDispatcher.RenderSection> nearbyVisibleSections,
    BitSet visibleSectionIndices
) {
    public boolean isSectionVisible(int index) {
        return index >= 0 && this.visibleSectionIndices.get(index);
    }
}
