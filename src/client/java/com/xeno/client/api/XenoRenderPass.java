package com.xeno.client.api;

import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * A callback interface that allows other mods to inject custom immediate-mode OpenGL/Blaze3D rendering
 * commands at designated stages of the world rendering loop.
 * <p>
 * If you need advanced frame graph declarations (specifying target buffers, read/write nodes, and dependencies),
 * prefer using {@link XenoFramePassBuilder} instead.
 * </p>
 *
 * @see XenoRenderAPI#registerRenderPass(Position, XenoRenderPass)
 */
public interface XenoRenderPass {
    
    /**
     * Represents the specific locations in the LevelRenderer loop where custom code can run.
     */
    enum Position {
        /** Run before any terrain blocks are drawn. Ideal for custom skyboxes or shadow-map passes. */
        BEFORE_TERRAIN,

        /** Run after solid block geometry rendering, but before translucent rendering. */
        AFTER_SOLID,

        /** Run after translucent blocks are rendered. Great for custom particles, water waves, or volumetric fog. */
        AFTER_TRANSLUCENT,

        /** Run after weather rendering (rain, snow). Good for custom atmospheric effects. */
        AFTER_WEATHER,

        /** Run after all world rendering finishes, before the HUD. Ideal for custom post-processing shaders. */
        POST_PROCESSING
    }

    /**
     * Called when the registered rendering stage is reached in the game loop.
     * <p>
     * Execute your custom OpenGL commands, buffer binds, or custom immediate shaders inside this callback.
     * </p>
     *
     * @param position the current rendering stage position
     * @param state    the current read-only {@link LevelRenderState} containing camera positions and world details
     */
    void render(Position position, LevelRenderState state);
}
