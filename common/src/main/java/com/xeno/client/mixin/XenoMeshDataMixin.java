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

package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.xeno.client.common.memory.MemoryAccess;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MeshData.class)
public abstract class XenoMeshDataMixin {
    /**
     * @author ExodusCoder9
     * @reason Read quad centroids from the off-heap buffer with Unsafe.
     **/
    @Overwrite
    public static void decodeQuadCentroids(
        ByteBuffer vertexBuffer, int vertexCount, VertexFormat format, CompactVectorArray output, int outputIndex
    ) {
        VertexFormatElement positionElement = format.getElement("Position");
        if (positionElement == null) {
            throw new IllegalArgumentException("Cannot identify quad centers with no position element");
        }

        long baseAddress = MemoryUtil.memAddress(vertexBuffer);
        int positionOffset = vertexBuffer.position() + positionElement.offset();
        int vertexStride = format.getVertexSize();
        int quadStride = vertexStride * 4;
        int quadCount = vertexCount / 4;

        for (int i = 0; i < quadCount; i++) {
            long firstPosOffset = baseAddress + (long) i * quadStride + positionOffset;
            long secondPosOffset = firstPosOffset + (long) vertexStride * 2;
            float x0 = MemoryAccess.getFloat(firstPosOffset);
            float y0 = MemoryAccess.getFloat(firstPosOffset + 4L);
            float z0 = MemoryAccess.getFloat(firstPosOffset + 8L);
            float x1 = MemoryAccess.getFloat(secondPosOffset);
            float y1 = MemoryAccess.getFloat(secondPosOffset + 4L);
            float z1 = MemoryAccess.getFloat(secondPosOffset + 8L);
            float xMid = (x0 + x1) / 2.0F;
            float yMid = (y0 + y1) / 2.0F;
            float zMid = (z0 + z1) / 2.0F;
            output.set(outputIndex + i, xMid, yMid, zMid);
        }
    }
}
