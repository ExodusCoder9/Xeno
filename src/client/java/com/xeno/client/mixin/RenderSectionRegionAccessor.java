package com.xeno.client.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to expose the private SectionCopy array from RenderSectionRegion.
 */
@Mixin(RenderSectionRegion.class)
public interface RenderSectionRegionAccessor {
    @Accessor("sections")
    SectionCopy[] xeno$getSections();
}
