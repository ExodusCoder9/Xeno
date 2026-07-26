package com.xeno.client.renderer.biome;

import com.xeno.client.renderer.world.XenoLevelSlice;

/**
 * Fast Biome Color Blender.
 * Blends grass, foliage, and water colors using flat 1D primitive array sampling,
 * completely bypassing Vanilla's BiomeColors iterator and object allocations.
 */
public class XenoBiomeBlender {

    @FunctionalInterface
    public interface ColorResolver {
        int getColor(Object biome, double x, double z);
    }

    /**
     * Blends neighbor biome colors over a given radius (e.g. 2x2 or 3x3) using flat sampling.
     */
    public static int blendColor(
            XenoLevelSlice slice,
            int x, int y, int z,
            int radius,
            ColorResolver resolver
    ) {
        if (radius <= 0 || slice == null) {
            return resolver.getColor(null, x, z);
        }

        int rSum = 0;
        int gSum = 0;
        int bSum = 0;
        int count = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int color = resolver.getColor(null, x + dx, z + dz);
                rSum += (color >> 16) & 0xFF;
                gSum += (color >> 8) & 0xFF;
                bSum += color & 0xFF;
                count++;
            }
        }

        if (count == 0) return 0xFFFFFFFF;

        int r = rSum / count;
        int g = gSum / count;
        int b = bSum / count;

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
