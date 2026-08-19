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

import com.xeno.client.common.memory.MemoryAccess;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CloudRenderer.class)
public abstract class XenoCloudRendererMixin {

    @Shadow
    private CloudRenderer.@Nullable TextureData texture;

    @Shadow
    private static boolean isNorthEmpty(final long cellData) { return false; }
    @Shadow
    private static boolean isEastEmpty(final long cellData) { return false; }
    @Shadow
    private static boolean isSouthEmpty(final long cellData) { return false; }
    @Shadow
    private static boolean isWestEmpty(final long cellData) { return false; }

    /**
     * @author ExodusCoder9
     * @reason Cloud Compression
     */
    @Overwrite
    private void buildMesh(
        final CloudRenderer.RelativeCameraPos relativePos,
        final ByteBuffer faceBuffer,
        final int centerCellX,
        final int centerCellZ,
        final boolean extrude,
        final int radiusCells
    ) {
        if (this.texture == null) {
            return;
        }

        long[] cells = this.texture.cells();
        int textureWidth = this.texture.width();
        int textureHeight = this.texture.height();
        
        long ptr = org.lwjgl.system.MemoryUtil.memAddress(faceBuffer);
        int quadCount = faceBuffer.position() / 3;

        for (int ring = 0; ring <= 2 * radiusCells; ring++) {
            for (int relativeCellX = -ring; relativeCellX <= ring; relativeCellX++) {
                int relativeCellZ = ring - Math.abs(relativeCellX);
                if (relativeCellZ >= 0 && relativeCellZ <= radiusCells && relativeCellX * relativeCellX + relativeCellZ * relativeCellZ <= radiusCells * radiusCells) {
                    if (relativeCellZ != 0) {
                        quadCount = xeno$tryBuildCellFast(ptr, quadCount, relativePos, centerCellX, centerCellZ, extrude, relativeCellX, textureWidth, -relativeCellZ, textureHeight, cells);
                    }
                    quadCount = xeno$tryBuildCellFast(ptr, quadCount, relativePos, centerCellX, centerCellZ, extrude, relativeCellX, textureWidth, relativeCellZ, textureHeight, cells);
                }
            }
        }
        
        faceBuffer.position(quadCount * 3);
    }

    @Unique
    private int xeno$tryBuildCellFast(
        long ptr,
        int quadCount,
        CloudRenderer.RelativeCameraPos relativePos,
        int cellX,
        int cellZ,
        boolean extrude,
        int relativeCellX,
        int textureWidth,
        int relativeCellZ,
        int textureHeight,
        long[] cells
    ) {
        int indexX = Math.floorMod(cellX + relativeCellX, textureWidth);
        int indexY = Math.floorMod(cellZ + relativeCellZ, textureHeight);
        long cellData = cells[indexX + indexY * textureWidth];
        
        if (cellData == 0L) {
            return quadCount;
        }

        if (extrude) {
            if (relativePos != CloudRenderer.RelativeCameraPos.BELOW_CLOUDS) {
                xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, Direction.UP, 0);
            }
            if (relativePos != CloudRenderer.RelativeCameraPos.ABOVE_CLOUDS) {
                xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, Direction.DOWN, 0);
            }
            if (isNorthEmpty(cellData) && relativeCellZ > 0) {
                xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, Direction.NORTH, 0);
            }
            if (isSouthEmpty(cellData) && relativeCellZ < 0) {
                xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, Direction.SOUTH, 0);
            }
            if (isWestEmpty(cellData) && relativeCellX > 0) {
                xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, Direction.WEST, 0);
            }
            if (isEastEmpty(cellData) && relativeCellX < 0) {
                xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, Direction.EAST, 0);
            }
            if (Math.abs(relativeCellX) <= 1 && Math.abs(relativeCellZ) <= 1) {
                for (Direction direction : Direction.values()) {
                    xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, direction, 16);
                }
            }
        } else {
            xeno$encodeFaceFast(ptr, quadCount++, relativeCellX, relativeCellZ, Direction.DOWN, 32);
        }
        
        return quadCount;
    }

    @Unique
    private void xeno$encodeFaceFast(long ptr, int quadIndex, int x, int z, Direction direction, int flags) {
        int dirAndFlags = direction.get3DDataValue() | flags;
        dirAndFlags |= (x & 1) << 7;
        dirAndFlags |= (z & 1) << 6;
        
        long dest = ptr + (quadIndex * 3L);
        MemoryAccess.putByte(dest + 0, (byte)(x >> 1));
        MemoryAccess.putByte(dest + 1, (byte)(z >> 1));
        MemoryAccess.putByte(dest + 2, (byte)dirAndFlags);
    }
}
