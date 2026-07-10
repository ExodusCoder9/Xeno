package com.xeno.client.mixin;

import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {
    @Invoker("getRenderSection")
    SectionRenderDispatcher.RenderSection invokeGetRenderSection(long sectionNode);

    @Accessor("sections")
    RotatingSectionStorage<SectionRenderDispatcher.RenderSection> getSections();
}
