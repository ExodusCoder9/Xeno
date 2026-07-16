package com.xeno.client.util;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class IgnoringViewArea extends ViewArea {
    private SectionPos lastCameraPos;

    public IgnoringViewArea(SectionRenderDispatcher sectionRenderDispatcher) {
        super(sectionRenderDispatcher, 0, 0, 0, 0, 0, null);
    }

    @Override
    public void releaseAllBuffers() {}

    @Override
    public boolean repositionCamera(SectionPos cameraSectionPos) {
        if (!cameraSectionPos.equals(this.lastCameraPos)) {
            this.lastCameraPos = cameraSectionPos;
            return true;
        }
        return false;
    }

    @Override
    public @Nullable RenderSection getRenderSectionAt(@NonNull BlockPos pos) {
        return null;
    }

    @Override
    protected @Nullable RenderSection getRenderSection(long sectionNode) {
        return null;
    }
}
