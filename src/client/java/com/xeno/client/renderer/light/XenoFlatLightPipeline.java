package com.xeno.client.renderer.light;

import net.minecraft.core.Direction;

/**
 * High-performance Flat Light Pipeline (used when Ambient Occlusion is OFF).
 * Computes directional face shading and single-sample block/sky light for quads
 * with zero vertex AO calculation overhead.
 */
public class XenoFlatLightPipeline {

    /**
     * Returns directional face brightness multiplier matching Minecraft face shading standards.
     */
    public static float getFaceShade(Direction face) {
        return switch (face) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    /**
     * Calculates flat 4-vertex brightness values for a face when AO is disabled.
     */
    public static void calculateFlatLighting(
            Direction face,
            float[] outBrightness
    ) {
        float shade = getFaceShade(face);
        outBrightness[0] = shade;
        outBrightness[1] = shade;
        outBrightness[2] = shade;
        outBrightness[3] = shade;
    }
}
