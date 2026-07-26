package com.xeno.client.renderer.light;

import com.xeno.client.renderer.world.XenoLevelSlice;
import net.minecraft.core.Direction;

/**
 * High-performance Smooth Light Pipeline (used when Ambient Occlusion is ON).
 * Computes 4-corner vertex AO weights and interpolates smooth lighting values
 * without allocating BlockPos objects or transient float wrappers.
 */
@SuppressWarnings("unused")
public class XenoSmoothLightPipeline {

    /**
     * Calculates smooth 4-corner vertex ambient occlusion values for a face.
     */
    public static void calculateSmoothLighting(
            XenoLevelSlice slice,
            int x, int y, int z,
            Direction face,
            float[] outAoValues
    ) {
        float faceShade = XenoFlatLightPipeline.getFaceShade(face);
        XenoLightPipeline.calculateFaceAo(slice, x, y, z, face, outAoValues);

        for (int i = 0; i < 4; i++) {
            outAoValues[i] *= faceShade;
        }
    }
}
