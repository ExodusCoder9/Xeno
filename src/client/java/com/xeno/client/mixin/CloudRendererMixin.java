package com.xeno.client.mixin;

import com.xeno.client.renderer.memory.MemoryIntrinsics;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Custom Cloud Renderer optimization mixin.
 * Replaces Vanilla's buildMesh with a direct memory manipulation version
 * equivalent to Sodium's cloud optimization.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Shadow
    @Final
    private static int FLAG_INSIDE_FACE;
    @Shadow
    @Final
    private static int FLAG_USE_TOP_COLOR;
    @Shadow
    private CloudRenderer.@Nullable TextureData texture;

    @Shadow
    private static boolean isNorthEmpty(long cellData) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isSouthEmpty(long cellData) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isWestEmpty(long cellData) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isEastEmpty(long cellData) {
        throw new AssertionError();
    }

    /**
     * @author Xeno
     * @reason Optimize cloud meshing by writing directly to buffer pointers
     */
    @Overwrite
    private void buildMesh(CloudRenderer.RelativeCameraPos relativeCameraPos,
                           ByteBuffer byteBuffer,
                           int cellX,
                           int cellZ,
                           boolean fancy,
                           int radius) {
        if (this.texture == null) {
            return;
        }

        long[] cells = this.texture.cells();
        int width = this.texture.width();
        int height = this.texture.height();

        // Use FFM API to get the pointer
        long ptr = MemorySegment.ofBuffer(byteBuffer).address();
        int cellIndex = byteBuffer.position() / 3;

        for (int ring = 0; ring <= 2 * radius; ++ring) {
            for (int dx = -ring; dx <= ring; ++dx) {
                int dz = ring - Math.abs(dx);
                if (dz >= 0 && dz <= radius && dx * dx + dz * dz <= radius * radius) {
                    if (dz != 0) {
                        cellIndex = xeno$addCellGeometryToBuffer(ptr, cellIndex, dx, -dz, relativeCameraPos,
                                fancy, cellX, cellZ, cells, width, height);
                    }

                    cellIndex = xeno$addCellGeometryToBuffer(ptr, cellIndex, dx, dz, relativeCameraPos,
                            fancy, cellX, cellZ, cells, width, height);
                }
            }
        }

        byteBuffer.position(cellIndex * 3);
    }

    @Unique
    private static int xeno$calculateTaxicabDistance(int x, int z) {
        return Math.abs(x) + Math.abs(z);
    }

    @Unique
    private static int xeno$addCellGeometryToBuffer(long ptr,
                                                      int index,
                                                      int x,
                                                      int z,
                                                      CloudRenderer.@Nullable RelativeCameraPos orientation,
                                                      boolean fancy,
                                                      int camX,
                                                      int camZ,
                                                      long[] cells,
                                                      int texWidth,
                                                      int texHeight) {
        int o = Math.floorMod(camX + x, texWidth);
        int p = Math.floorMod(camZ + z, texHeight);
        long faces = cells[o + p * texWidth];

        if (faces == 0) {
            return index;
        }

        int newIndex = index;

        if (fancy) {
            newIndex = xeno$emitCellGeometryExterior(ptr, newIndex, faces, orientation, x, z);

            if (xeno$calculateTaxicabDistance(x, z) <= 1) {
                newIndex = xeno$emitCellGeometryInterior(ptr, newIndex, x, z);
            }
        } else {
            xeno$encodeCellFace(ptr, newIndex, x, z, Direction.DOWN, FLAG_USE_TOP_COLOR);
            newIndex++;
        }

        return newIndex;
    }

    @Unique
    private static int xeno$emitCellGeometryInterior(long ptr, int index, int x, int z) {
        xeno$encodeCellFace(ptr, index, x, z, Direction.DOWN, FLAG_INSIDE_FACE);
        xeno$encodeCellFace(ptr, index + 1, x, z, Direction.UP, FLAG_INSIDE_FACE);
        xeno$encodeCellFace(ptr, index + 2, x, z, Direction.NORTH, FLAG_INSIDE_FACE);
        xeno$encodeCellFace(ptr, index + 3, x, z, Direction.SOUTH, FLAG_INSIDE_FACE);
        xeno$encodeCellFace(ptr, index + 4, x, z, Direction.WEST, FLAG_INSIDE_FACE);
        xeno$encodeCellFace(ptr, index + 5, x, z, Direction.EAST, FLAG_INSIDE_FACE);

        return index + 6;
    }

    @Unique
    private static void xeno$encodeCellFace(long ptr, long index, int x, int z, Direction direction, int extraData) {
        int flags = direction.get3DDataValue() | extraData;
        flags |= (x & 1) << 7;
        flags |= (z & 1) << 6;

        long ptrIndex = ptr + (index * 3);

        MemoryIntrinsics.putByte(ptrIndex, (byte) (x >> 1));
        MemoryIntrinsics.putByte(ptrIndex + 1, (byte) (z >> 1));
        MemoryIntrinsics.putByte(ptrIndex + 2, (byte) flags);
    }

    @Unique
    private static int xeno$emitCellGeometryExterior(long ptr,
                                                       int index,
                                                       long faces,
                                                       CloudRenderer.@Nullable RelativeCameraPos orientation,
                                                       int x,
                                                       int z) {
        int faceCount = index;

        if (orientation != CloudRenderer.RelativeCameraPos.BELOW_CLOUDS) {
            xeno$encodeCellFace(ptr, faceCount, x, z, Direction.UP, 0);
            faceCount += 1;
        }

        if (orientation != CloudRenderer.RelativeCameraPos.ABOVE_CLOUDS) {
            xeno$encodeCellFace(ptr, faceCount, x, z, Direction.DOWN, 0);
            faceCount += 1;
        }

        if (isNorthEmpty(faces) && z > 0) {
            xeno$encodeCellFace(ptr, faceCount, x, z, Direction.NORTH, 0);
            faceCount += 1;
        }

        if (isSouthEmpty(faces) && z < 0) {
            xeno$encodeCellFace(ptr, faceCount, x, z, Direction.SOUTH, 0);
            faceCount += 1;
        }

        if (isWestEmpty(faces) && x > 0) {
            xeno$encodeCellFace(ptr, faceCount, x, z, Direction.WEST, 0);
            faceCount += 1;
        }

        if (isEastEmpty(faces) && x < 0) {
            xeno$encodeCellFace(ptr, faceCount, x, z, Direction.EAST, 0);
            faceCount += 1;
        }

        return faceCount;
    }
}
