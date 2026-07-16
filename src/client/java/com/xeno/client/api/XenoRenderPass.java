package com.xeno.client.api;

import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * A callback interface allowing other mods to inject custom OpenGL/Blaze3D rendering
 * commands at specific stages of the Level Renderer.
 */
public interface XenoRenderPass {
    
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
     * Called when the registered rendering stage is reached.
     * @param position The current rendering stage.
     * @param state The current level render state.
     */
    void render(Position position, LevelRenderState state);
}
