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

package com.xeno.client.common.render.culling;

public final class XenoDirectionalVisGraph {
    public static final long FULL_VISIBILITY_MASK = (1L << 36) - 1L;
    private static final int SECTION_VOLUME = 4096;
    private static final int MIN_OPAQUE_TO_OCCLUDE = 256;

    private static final int STEP_X = 1;
    private static final int STEP_Z = 16;
    private static final int STEP_Y = 256;

    private static final int FACE_DOWN = 0;
    private static final int FACE_UP = 1;
    private static final int FACE_NORTH = 2;
    private static final int FACE_SOUTH = 3;
    private static final int FACE_WEST = 4;
    private static final int FACE_EAST = 5;

    private static final int[] OPPOSITE_FACE = {1, 0, 3, 2, 5, 4};

    private static final int[] BASIS_OCTANT_MOVE_DIRS = {
            (1 << FACE_WEST) | (1 << FACE_DOWN) | (1 << FACE_NORTH),
            (1 << FACE_WEST) | (1 << FACE_UP) | (1 << FACE_NORTH),
            (1 << FACE_WEST) | (1 << FACE_DOWN) | (1 << FACE_SOUTH),
            (1 << FACE_EAST) | (1 << FACE_DOWN) | (1 << FACE_NORTH)
    };

    private static final long[] SINGLE_FULL_MASK = {FULL_VISIBILITY_MASK};
    private static final long[] SINGLE_EMPTY_MASK = {0L};

    private final long[] opaqueBits = new long[64];
    private final long[] visitedBits = new long[64];
    private final short[] floodStack = new short[SECTION_VOLUME];
    private int opaqueCount;

    public void reset() {
        java.util.Arrays.fill(this.opaqueBits, 0L);
        this.opaqueCount = 0;
    }

    public void setOpaque(int relX, int relY, int relZ) {
        int bitIdx = relX | (relZ << 4) | (relY << 8);
        int word = bitIdx >>> 6;
        long mask = 1L << (bitIdx & 63);
        if ((this.opaqueBits[word] & mask) == 0L) {
            this.opaqueBits[word] |= mask;
            this.opaqueCount++;
        }
    }

    public long[] resolve() {
        if (this.opaqueCount >= SECTION_VOLUME) {
            return SINGLE_EMPTY_MASK;
        }
        if (this.opaqueCount < MIN_OPAQUE_TO_OCCLUDE) {
            return SINGLE_FULL_MASK;
        }
        int openFaces = this.computeOpenFacesMask();
        if (openFaces == 0) {
            return SINGLE_EMPTY_MASK;
        }
        long[] masks = new long[4];
        for (int i = 0; i < 4; i++) {
            masks[i] = this.solveBasisOctant(BASIS_OCTANT_MOVE_DIRS[i], openFaces);
        }
        return masks;
    }

    private int computeOpenFacesMask() {
        int open = 0;
        long[] bits = this.opaqueBits;
        if ((bits[0] & bits[1] & bits[2] & bits[3]) != -1L) {
            open |= 1 << FACE_DOWN;
        }
        if ((bits[60] & bits[61] & bits[62] & bits[63]) != -1L) {
            open |= 1 << FACE_UP;
        }
        for (int y = 0; y < 16; y++) {
            if ((bits[y << 2] & 0xFFFFL) != 0xFFFFL) {
                open |= 1 << FACE_NORTH;
                break;
            }
        }
        for (int y = 0; y < 16; y++) {
            if ((bits[(y << 2) | 3] & 0xFFFF000000000000L) != 0xFFFF000000000000L) {
                open |= 1 << FACE_SOUTH;
                break;
            }
        }
        for (int w = 0; w < 64; w++) {
            if ((bits[w] & 0x0001000100010001L) != 0x0001000100010001L) {
                open |= 1 << FACE_WEST;
                break;
            }
        }
        for (int w = 0; w < 64; w++) {
            if ((bits[w] & 0x8000800080008000L) != 0x8000800080008000L) {
                open |= 1 << FACE_EAST;
                break;
            }
        }
        return open;
    }

    private long solveBasisOctant(int allowedMoves, int openFaces) {
        int seedFaces = (~allowedMoves) & 0x3F & openFaces;
        long mask = 0L;

        while (seedFaces != 0) {
            int originFace = Integer.numberOfTrailingZeros(seedFaces);
            seedFaces &= ~(1 << originFace);

            int minX = 0;
            int minY = 0;
            int minZ = 0;
            int maxX = 15;
            int maxY = 15;
            int maxZ = 15;
            switch (originFace) {
                case FACE_DOWN -> maxY = 0;
                case FACE_UP -> minY = 15;
                case FACE_NORTH -> maxZ = 0;
                case FACE_SOUTH -> minZ = 15;
                case FACE_WEST -> maxX = 0;
                case FACE_EAST -> minX = 15;
                default -> {
                }
            }

            System.arraycopy(this.opaqueBits, 0, this.visitedBits, 0, 64);
            int touchedFaces = 0;

            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        int idx = x | (z << 4) | (y << 8);
                        if (this.markVisited(idx)) {
                            touchedFaces |= this.floodFill(idx, allowedMoves);
                        }
                    }
                }
            }

            int enterMove = OPPOSITE_FACE[originFace];
            while (touchedFaces != 0) {
                int exitFace = Integer.numberOfTrailingZeros(touchedFaces);
                touchedFaces &= ~(1 << exitFace);
                mask |= 1L << (exitFace * 6 + enterMove);
                mask |= 1L << (originFace * 6 + OPPOSITE_FACE[exitFace]);
            }
        }

        return mask;
    }

    private boolean markVisited(int idx) {
        int word = idx >>> 6;
        long bit = 1L << (idx & 63);
        long prev = this.visitedBits[word];
        if ((prev & bit) != 0L) {
            return false;
        }
        this.visitedBits[word] = prev | bit;
        return true;
    }

    private int floodFill(int startIdx, int allowedMoves) {
        int sp = 0;
        this.floodStack[sp++] = (short) startIdx;
        int reachedFaces = 0;
        boolean moveDown = (allowedMoves & (1 << FACE_DOWN)) != 0;
        boolean moveUp = (allowedMoves & (1 << FACE_UP)) != 0;
        boolean moveNorth = (allowedMoves & (1 << FACE_NORTH)) != 0;
        boolean moveSouth = (allowedMoves & (1 << FACE_SOUTH)) != 0;
        boolean moveWest = (allowedMoves & (1 << FACE_WEST)) != 0;
        boolean moveEast = (allowedMoves & (1 << FACE_EAST)) != 0;

        while (sp > 0) {
            int pos = this.floodStack[--sp] & 0xFFF;
            int x = pos & 15;
            int z = (pos >>> 4) & 15;
            int y = (pos >>> 8) & 15;

            if (y == 0) {
                reachedFaces |= 1 << FACE_DOWN;
            } else if (moveDown && this.markVisited(pos - STEP_Y)) {
                this.floodStack[sp++] = (short) (pos - STEP_Y);
            }

            if (y == 15) {
                reachedFaces |= 1 << FACE_UP;
            } else if (moveUp && this.markVisited(pos + STEP_Y)) {
                this.floodStack[sp++] = (short) (pos + STEP_Y);
            }

            if (z == 0) {
                reachedFaces |= 1 << FACE_NORTH;
            } else if (moveNorth && this.markVisited(pos - STEP_Z)) {
                this.floodStack[sp++] = (short) (pos - STEP_Z);
            }

            if (z == 15) {
                reachedFaces |= 1 << FACE_SOUTH;
            } else if (moveSouth && this.markVisited(pos + STEP_Z)) {
                this.floodStack[sp++] = (short) (pos + STEP_Z);
            }

            if (x == 0) {
                reachedFaces |= 1 << FACE_WEST;
            } else if (moveWest && this.markVisited(pos - STEP_X)) {
                this.floodStack[sp++] = (short) (pos - STEP_X);
            }

            if (x == 15) {
                reachedFaces |= 1 << FACE_EAST;
            } else if (moveEast && this.markVisited(pos + STEP_X)) {
                this.floodStack[sp++] = (short) (pos + STEP_X);
            }
        }

        return reachedFaces;
    }
}
