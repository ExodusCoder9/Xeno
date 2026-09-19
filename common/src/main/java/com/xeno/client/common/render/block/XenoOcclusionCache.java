/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.common.render.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class XenoOcclusionCache {
    private static final int CACHE_SIZE = 512;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    private final VoxelShape[] shapeKeysA = new VoxelShape[CACHE_SIZE];
    private final VoxelShape[] shapeKeysB = new VoxelShape[CACHE_SIZE];
    private final byte[] directionKeys = new byte[CACHE_SIZE];
    private final boolean[] cachedResults = new boolean[CACHE_SIZE];

    public static boolean isEmpty(VoxelShape shape) {
        return shape == Shapes.empty() || shape.isEmpty();
    }

    public static boolean isFullCube(VoxelShape shape) {
        return shape == Shapes.block();
    }

    public boolean occludes(VoxelShape shapeA, VoxelShape shapeB, Direction direction) {
        if (isEmpty(shapeA) || isEmpty(shapeB)) {
            return false;
        }
        if (isFullCube(shapeB)) {
            return isFullCube(shapeA);
        }

        int hash = (System.identityHashCode(shapeA) * 31 + System.identityHashCode(shapeB)) * 31 + direction.ordinal();
        int index = hash & CACHE_MASK;

        if (this.shapeKeysA[index] == shapeA && this.shapeKeysB[index] == shapeB && this.directionKeys[index] == (byte) direction.ordinal()) {
            return this.cachedResults[index];
        }

        boolean result = Shapes.blockOccludes(shapeA, shapeB, direction);
        this.shapeKeysA[index] = shapeA;
        this.shapeKeysB[index] = shapeB;
        this.directionKeys[index] = (byte) direction.ordinal();
        this.cachedResults[index] = result;

        return result;
    }
}
