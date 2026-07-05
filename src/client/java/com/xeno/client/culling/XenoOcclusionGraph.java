package com.xeno.client.culling;

import java.util.List;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public interface XenoOcclusionGraph {
    boolean xeno$isSectionVisible(int sectionIndex);
    List<SectionRenderDispatcher.RenderSection> xeno$getSolidSections();
    List<SectionRenderDispatcher.RenderSection> xeno$getCutoutSections();
    List<SectionRenderDispatcher.RenderSection> xeno$getTranslucentSections();
}
