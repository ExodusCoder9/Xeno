package com.xeno.client.api;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * Functional callback interface designed to construct passes inside Minecraft's native
 * Blaze3D DAG frame graph (`FrameGraphBuilder`).
 * <p>
 * Using this builder allows mods to declare custom passes, specify buffer inputs/outputs,
 * read render targets (like the translucent buffer or weather buffer), write to the main
 * target, and record graphics-agnostic drawing commands.
 * </p>
 * <p>
 * <strong>Compatibility Advantage:</strong> Unlike raw immediate OpenGL, frame graphs are
 * backend-independent, meaning custom passes registered through this interface will work
 * cleanly on **Vulkan, Metal, or OpenGL** backends.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * XenoRenderAPI.registerFramePassBuilder((frame, targets, state) -> {
 *     FramePass myPass = frame.addPass("volumetric_fog");
 *     targets.main = myPass.readsAndWrites(targets.main); // declare target write dependency
 *     myPass.executes(() -> {
 *         // execute render calls on the GPU
 *     });
 * });
 * }</pre>
 *
 * @see XenoRenderAPI#registerFramePassBuilder(XenoFramePassBuilder)
 */
@FunctionalInterface
public interface XenoFramePassBuilder {
    
    /**
     * Invoked during the LevelRenderer's frame graph construction phase.
     * <p>
     * Modders should register their custom frame passes onto the provided builder.
     * </p>
     *
     * @param frame   the active {@link FrameGraphBuilder} for the frame
     * @param targets the {@link LevelTargetBundle} containing imported resource handles (color, depth, etc.)
     * @param state   the read-only {@link LevelRenderState} for the frame
     */
    void buildPasses(FrameGraphBuilder frame, LevelTargetBundle targets, LevelRenderState state);
}
