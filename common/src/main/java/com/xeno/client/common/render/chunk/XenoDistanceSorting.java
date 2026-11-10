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
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.common.render.chunk;

import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.jspecify.annotations.NonNull;

public record XenoDistanceSorting(float camX, float camY, float camZ) implements VertexSorting {
    @Override
    public int @NonNull [] sort(@NonNull CompactVectorArray points) {
        final int count = points.size();
        if (count <= 1) {
            final int[] result = new int[count];
            if (count == 1) {
                result[0] = 0;
            }
            return result;
        }

        final int[] indices = new int[count];
        final float[] distances = new float[count];
        for (int i = 0; i < count; i++) {
            final float dx = points.getX(i) - this.camX;
            final float dy = points.getY(i) - this.camY;
            final float dz = points.getZ(i) - this.camZ;
            distances[i] = dx * dx + dy * dy + dz * dz;
            indices[i] = i;
        }

        for (int i = 1; i < count; i++) {
            final int q = indices[i];
            final float d = distances[q];
            int j = i - 1;
            while (j >= 0 && distances[indices[j]] < d) {
                indices[j + 1] = indices[j];
                j--;
            }
            indices[j + 1] = q;
        }

        return indices;
    }
}
