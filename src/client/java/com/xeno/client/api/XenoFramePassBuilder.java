package com.xeno.client.api;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * Interface for registering custom frame graph passes.
 * Allows other mods to declare inputs/outputs and append operations directly into
 * Mojang's modern frame-graph rendering pipeline. This supports OpenGL, Vulkan, and Metal backends.
 */
@FunctionalInterface
public interface XenoFramePassBuilder {
    /**
     * Called during LevelRenderer's frame graph construction phase.
     * @param frame The Mojang Frame Graph Builder.
     * @param targets The Level Target Bundle containing resources (like main target, translucency, entity outline targets).
     * @param state The Level Render State.
     */
    void buildPasses(FrameGraphBuilder frame, LevelTargetBundle targets, LevelRenderState state);
}
