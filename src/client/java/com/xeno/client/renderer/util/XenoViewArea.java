package com.xeno.client.renderer.util;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.multiplayer.ClientLevel;

public class XenoViewArea extends ViewArea {
    public XenoViewArea(
            SectionRenderDispatcher sectionRenderDispatcher,
            ClientLevel level,
            int viewDistance,
            SectionOcclusionGraph occlusionGraph
    ) {
        super(
                sectionRenderDispatcher,
                level.getMinY(),
                level.getMaxY(),
                level.getMinSectionY(),
                level.getMaxSectionY(),
                viewDistance,
                occlusionGraph
        );
    }
}
