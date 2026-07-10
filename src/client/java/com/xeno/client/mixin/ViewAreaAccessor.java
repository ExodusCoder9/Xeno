package com.xeno.client.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {
    @Accessor("sections")
    net.minecraft.client.RotatingSectionStorage<SectionRenderDispatcher.RenderSection> xeno$getSections();
}
