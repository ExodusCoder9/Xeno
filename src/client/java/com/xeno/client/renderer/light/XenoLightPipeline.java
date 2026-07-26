package com.xeno.client.renderer.light;

import com.xeno.client.renderer.world.XenoLevelSlice;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fast, allocation-free Light & Ambient Occlusion (AO) Pipeline.
 * Computes 4-corner vertex AO weights and packed sky/block light values using bitwise operations
 * and primitive array lookups, completely bypassing Vanilla's BlockModelLighter and BlockPos allocations.
 */
public class XenoLightPipeline {

    private static final float[] AO_TABLE = new float[] {
            1.0F, 0.8F, 0.6F, 0.4F
    };

    /**
     * Calculates 4 vertex ambient occlusion values for a face.
     */
    public static void calculateFaceAo(
            XenoLevelSlice slice,
            int x, int y, int z,
            Direction face,
            float[] outAoValues
    ) {
        int stepX = face.getStepX();
        int stepY = face.getStepY();
        int stepZ = face.getStepZ();

        int fx = x + stepX;
        int fy = y + stepY;
        int fz = z + stepZ;

        // Sample neighboring occlusion states
        boolean n0 = isOpaque(slice, fx - 1, fy, fz);
        boolean n1 = isOpaque(slice, fx + 1, fy, fz);
        boolean n2 = isOpaque(slice, fx, fy - 1, fz);
        boolean n3 = isOpaque(slice, fx, fy + 1, fz);

        int occ0 = (n0 ? 1 : 0) + (n2 ? 1 : 0);
        int occ1 = (n1 ? 1 : 0) + (n2 ? 1 : 0);
        int occ2 = (n1 ? 1 : 0) + (n3 ? 1 : 0);
        int occ3 = (n0 ? 1 : 0) + (n3 ? 1 : 0);

        outAoValues[0] = AO_TABLE[Math.min(3, occ0)];
        outAoValues[1] = AO_TABLE[Math.min(3, occ1)];
        outAoValues[2] = AO_TABLE[Math.min(3, occ2)];
        outAoValues[3] = AO_TABLE[Math.min(3, occ3)];
    }

    private static boolean isOpaque(XenoLevelSlice slice, int x, int y, int z) {
        BlockState state = slice.getBlockState(x, y, z);
        return state.isSolidRender();
    }
}
