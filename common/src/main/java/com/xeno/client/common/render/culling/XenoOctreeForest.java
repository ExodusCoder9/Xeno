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

import com.xeno.client.common.render.texture.XenoCompiledSectionMeshExtension;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class XenoOctreeForest {
    private static final int TILE_SHIFT = 6;
    private static final int TILE_DIM = 64;
    private static final int TILE_MASK = 63;
    private static final int NEARBY_PADDING = 32;

    private int minSecX;
    private int minSecY;
    private int minSecZ;
    private int maxSecX;
    private int maxSecY;
    private int maxSecZ;
    private int tilesX;
    private int tilesY;
    private int tilesZ;
    private @Nullable Tile[] tiles;
    private XenoOcclusionCuller.GridLayout grid;
    private int centerSecX;
    private int centerSecZ;
    private int sectionCount;
    private long[] portalBits = new long[0];
    private int portalWordCount;

    private Frustum ctxFrustum;
    private @Nullable XenoBranchlessFrustum ctxBf;
    private float ctxBfCamX;
    private float ctxBfCamY;
    private float ctxBfCamZ;
    private int ctxCamBlockX;
    private int ctxCamBlockY;
    private int ctxCamBlockZ;
    private RenderSection[] ctxGridSections;
    private @Nullable List<RenderSection> ctxOutVisible;
    private @Nullable List<RenderSection> ctxOutNearby;
    private int ctxRelCamSecX;
    private int ctxRelCamSecY;
    private int ctxRelCamSecZ;

    public XenoOctreeForest(
            XenoOcclusionCuller.GridLayout grid,
            int centerSecX,
            int centerSecZ
    ) {
        this.tiles = new Tile[0];
        this.resetFor(grid, centerSecX, centerSecZ);
    }

    public void resetFor(
            XenoOcclusionCuller.GridLayout grid,
            int centerSecX,
            int centerSecZ
    ) {
        this.grid = grid;
        this.centerSecX = centerSecX;
        this.centerSecZ = centerSecZ;
        int vd = grid.viewDistance();
        this.minSecX = centerSecX - vd;
        this.maxSecX = centerSecX + vd;
        this.minSecY = grid.minSectionY();
        this.maxSecY = grid.maxSectionY();
        this.minSecZ = centerSecZ - vd;
        this.maxSecZ = centerSecZ + vd;
        this.tilesX = ((this.maxSecX - this.minSecX) >>> TILE_SHIFT) + 1;
        this.tilesY = ((this.maxSecY - this.minSecY) >>> TILE_SHIFT) + 1;
        this.tilesZ = ((this.maxSecZ - this.minSecZ) >>> TILE_SHIFT) + 1;
        int neededTiles = this.tilesX * this.tilesY * this.tilesZ;
        if (this.tiles.length < neededTiles) {
            Tile[] grown = new Tile[neededTiles];
            System.arraycopy(this.tiles, 0, grown, 0, this.tiles.length);
            this.tiles = grown;
        }
        for (int tz = 0; tz < this.tilesZ; tz++) {
            for (int ty = 0; ty < this.tilesY; ty++) {
                for (int tx = 0; tx < this.tilesX; tx++) {
                    Tile tile = this.tiles[(tz * this.tilesY + ty) * this.tilesX + tx];
                    if (tile != null) {
                        tile.resetOrigin(
                                this.minSecX + (tx << TILE_SHIFT),
                                this.minSecY + (ty << TILE_SHIFT),
                                this.minSecZ + (tz << TILE_SHIFT)
                        );
                    }
                }
            }
        }
        this.sectionCount = 0;
    }

    public void add(int secX, int secY, int secZ) {
        if (secX < this.minSecX || secX > this.maxSecX
                || secY < this.minSecY || secY > this.maxSecY
                || secZ < this.minSecZ || secZ > this.maxSecZ) {
            return;
        }
        int rx = secX - this.minSecX;
        int ry = secY - this.minSecY;
        int rz = secZ - this.minSecZ;
        int tx = rx >>> TILE_SHIFT;
        int ty = ry >>> TILE_SHIFT;
        int tz = rz >>> TILE_SHIFT;
        int tIdx = (tz * this.tilesY + ty) * this.tilesX + tx;
        Tile tile = this.tiles[tIdx];
        if (tile == null) {
            tile = new Tile(
                    this.minSecX + (tx << TILE_SHIFT),
                    this.minSecY + (ty << TILE_SHIFT),
                    this.minSecZ + (tz << TILE_SHIFT)
            );
            this.tiles[tIdx] = tile;
        }
        if (tile.add(rx & TILE_MASK, ry & TILE_MASK, rz & TILE_MASK)) {
            this.sectionCount++;
        }
    }

    public void prepare() {
        int limit = this.tilesX * this.tilesY * this.tilesZ;
        for (int i = 0; i < limit; i++) {
            Tile tile = this.tiles[i];
            if (tile != null) {
                tile.prepare();
            }
        }
    }

    public int getSectionCount() {
        return this.sectionCount;
    }

    public void copyPortalBits(long[] src, int wordCount) {
        if (this.portalBits.length < wordCount) {
            this.portalBits = new long[wordCount];
        }
        System.arraycopy(src, 0, this.portalBits, 0, wordCount);
        this.portalWordCount = wordCount;
    }

    public boolean isBoxReachable(double x1, double y1, double z1, double x2, double y2, double z2) {
        if (this.portalWordCount == 0) {
            return true;
        }
        if (y2 < this.grid.minBlockY() + 0.5 || y1 > this.grid.maxBlockY() - 0.5) {
            return true;
        }
        int minX = SectionPos.posToSectionCoord(x1 - 0.5);
        int minY = Math.max(this.minSecY, SectionPos.posToSectionCoord(y1 - 0.5));
        int minZ = SectionPos.posToSectionCoord(z1 - 0.5);
        int maxX = SectionPos.posToSectionCoord(x2 + 0.5);
        int maxY = Math.min(this.maxSecY, SectionPos.posToSectionCoord(y2 + 0.5));
        int maxZ = SectionPos.posToSectionCoord(z2 + 0.5);

        if (minX < this.minSecX || maxX > this.maxSecX || minZ < this.minSecZ || maxZ > this.maxSecZ) {
            return true;
        }

        int gridSizeXZ = this.grid.gridSizeXZ();
        int gridSizeY = this.grid.gridSizeY();
        int strideZ = gridSizeY * gridSizeXZ;
        int minGridY = this.grid.minSectionY();

        if (minX == maxX && minY == maxY && minZ == maxZ) {
            int modX = minX % gridSizeXZ;
            if (modX < 0) modX += gridSizeXZ;
            int modZ = minZ % gridSizeXZ;
            if (modZ < 0) modZ += gridSizeXZ;
            int idx = modZ * strideZ + (minY - minGridY) * gridSizeXZ + modX;
            int word = idx >>> 6;
            return word >= this.portalWordCount || (this.portalBits[word] & (1L << idx)) != 0L;
        }

        int yOffStart = minY - minGridY;
        int yOffEnd = maxY - minGridY;
        for (int x = minX; x <= maxX; x++) {
            int modX = x % gridSizeXZ;
            if (modX < 0) modX += gridSizeXZ;
            for (int z = minZ; z <= maxZ; z++) {
                int modZ = z % gridSizeXZ;
                if (modZ < 0) modZ += gridSizeXZ;
                int base = modZ * strideZ + modX;
                for (int yOff = yOffStart; yOff <= yOffEnd; yOff++) {
                    int idx = base + yOff * gridSizeXZ;
                    int word = idx >>> 6;
                    if (word >= this.portalWordCount || (this.portalBits[word] & (1L << idx)) != 0L) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void traverse(
            Frustum frustum,
            @Nullable XenoBranchlessFrustum bf,
            int camSecX,
            int camSecY,
            int camSecZ,
            int camBlockX,
            int camBlockY,
            int camBlockZ,
            @Nullable List<RenderSection> outVisible,
            @Nullable List<RenderSection> outNearby
    ) {
        if (this.sectionCount == 0) {
            return;
        }
        this.ctxFrustum = frustum;
        this.ctxBf = bf;
        this.ctxBfCamX = bf != null ? (float) bf.getCamX() : 0.0f;
        this.ctxBfCamY = bf != null ? (float) bf.getCamY() : 0.0f;
        this.ctxBfCamZ = bf != null ? (float) bf.getCamZ() : 0.0f;
        this.ctxCamBlockX = camBlockX;
        this.ctxCamBlockY = camBlockY;
        this.ctxCamBlockZ = camBlockZ;
        this.ctxGridSections = this.grid.sections();
        this.ctxOutVisible = outVisible;
        this.ctxOutNearby = outNearby;

        int centerTileX = Math.max(0, Math.min(this.tilesX - 1, (camSecX - this.minSecX) >>> TILE_SHIFT));
        int centerTileY = Math.max(0, Math.min(this.tilesY - 1, (camSecY - this.minSecY) >>> TILE_SHIFT));
        int centerTileZ = Math.max(0, Math.min(this.tilesZ - 1, (camSecZ - this.minSecZ) >>> TILE_SHIFT));

        for (int dzStep = 0; dzStep < this.tilesZ; dzStep++) {
            int tz = this.tilesZ == 1 ? 0 : (dzStep == 0 ? centerTileZ : (centerTileZ + dzStep) % this.tilesZ);
            for (int dyStep = 0; dyStep < this.tilesY; dyStep++) {
                int ty = this.tilesY == 1 ? 0 : (dyStep == 0 ? centerTileY : (centerTileY + dyStep) % this.tilesY);
                for (int dxStep = 0; dxStep < this.tilesX; dxStep++) {
                    int tx = this.tilesX == 1 ? 0 : (dxStep == 0 ? centerTileX : (centerTileX + dxStep) % this.tilesX);
                    Tile tile = this.tiles[(tz * this.tilesY + ty) * this.tilesX + tx];
                    if (tile == null || tile.top == 0L) {
                        continue;
                    }
                    tile.traverse(camSecX, camSecY, camSecZ);
                }
            }
        }

        this.ctxFrustum = null;
        this.ctxBf = null;
        this.ctxGridSections = null;
        this.ctxOutVisible = null;
        this.ctxOutNearby = null;
    }

    private static int interleave6x3(int x, int y, int z) {
        return interleave6(x) | (interleave6(y) << 1) | (interleave6(z) << 2);
    }

    private static int interleave6(int n) {
        n &= 0b111111;
        n = (n | (n << 4) | (n << 8)) & 0b000011000011000011;
        n = (n | (n << 2)) & 0b001001001001001001;
        return n;
    }

    private final class Tile {
        private int originSecX;
        private int originSecY;
        private int originSecZ;
        private final long[] leaves = new long[TILE_DIM * TILE_DIM];
        private final long[] mid = new long[TILE_DIM];
        private long top;

        Tile(int originSecX, int originSecY, int originSecZ) {
            this.originSecX = originSecX;
            this.originSecY = originSecY;
            this.originSecZ = originSecZ;
        }

        void resetOrigin(int originSecX, int originSecY, int originSecZ) {
            this.originSecX = originSecX;
            this.originSecY = originSecY;
            this.originSecZ = originSecZ;
            long topBits = this.top;
            while (topBits != 0L) {
                int i = Long.numberOfTrailingZeros(topBits);
                topBits &= topBits - 1L;
                int base = i << 6;
                long midBits = this.mid[i];
                while (midBits != 0L) {
                    int j = Long.numberOfTrailingZeros(midBits);
                    midBits &= midBits - 1L;
                    this.leaves[base + j] = 0L;
                }
                this.mid[i] = 0L;
            }
            this.top = 0L;
        }

        boolean add(int lx, int ly, int lz) {
            int morton = interleave6x3(lx, ly, lz);
            int word = morton >>> 6;
            long bit = 1L << (morton & 63);
            long prev = this.leaves[word];
            if ((prev & bit) != 0L) {
                return false;
            }
            this.leaves[word] = prev | bit;
            return true;
        }

        void prepare() {
            long topMask = 0L;
            for (int i = 0; i < 64; i++) {
                long midMask = 0L;
                int base = i << 6;
                for (int j = 0; j < 64; j++) {
                    if (this.leaves[base + j] != 0L) {
                        midMask |= 1L << j;
                    }
                }
                this.mid[i] = midMask;
                if (midMask != 0L) {
                    topMask |= 1L << i;
                }
            }
            this.top = topMask;
        }

        void traverse(int camSecX, int camSecY, int camSecZ) {
            XenoOctreeForest.this.ctxRelCamSecX = camSecX - this.originSecX;
            XenoOctreeForest.this.ctxRelCamSecY = camSecY - this.originSecY;
            XenoOctreeForest.this.ctxRelCamSecZ = camSecZ - this.originSecZ;

            XenoBranchlessFrustum bf = XenoOctreeForest.this.ctxBf;
            boolean tileVisible = true;
            boolean insideFrustum = false;
            double minX = this.originSecX << 4;
            double minY = this.originSecY << 4;
            double minZ = this.originSecZ << 4;
            if (bf != null) {
                int vis = bf.cubeInFrustum(minX, minY, minZ, minX + 1024.0, minY + 1024.0, minZ + 1024.0);
                if (vis == XenoBranchlessFrustum.OUTSIDE) {
                    tileVisible = false;
                } else {
                    insideFrustum = vis == XenoBranchlessFrustum.INSIDE;
                }
            } else {
                tileVisible = XenoOctreeForest.this.ctxFrustum.isVisible(new AABB(minX, minY, minZ, minX + 1024.0, minY + 1024.0, minZ + 1024.0));
            }
            if (!tileVisible) {
                return;
            }

            this.visitNode(5, 0, 0, 0, 0, insideFrustum);
        }

        private void visitNode(
                int level,
                int mortonBase,
                int baseX,
                int baseY,
                int baseZ,
                boolean parentInside
        ) {
            int halfSpan = 1 << level;
            int orderMod = ((XenoOctreeForest.this.ctxRelCamSecZ >= baseZ + halfSpan) ? 4 : 0)
                    | ((XenoOctreeForest.this.ctxRelCamSecY >= baseY + halfSpan) ? 2 : 0)
                    | ((XenoOctreeForest.this.ctxRelCamSecX >= baseX + halfSpan) ? 1 : 0);

            long summaryWord;
            int bitBase;
            boolean oddLevel = (level & 1) != 0;
            if (level >= 4) {
                summaryWord = this.top;
                bitBase = (mortonBase >>> 12) & 63;
            } else if (level >= 2) {
                summaryWord = this.mid[mortonBase >>> 12];
                bitBase = (mortonBase >>> 6) & 63;
            } else {
                summaryWord = this.leaves[mortonBase >>> 6];
                bitBase = mortonBase & 63;
            }

            if (summaryWord == 0L) {
                return;
            }

            if (level == 0) {
                int leafMask = (int) (summaryWord >>> bitBase) & 0xFF;
                if (leafMask == 0) {
                    return;
                }
                XenoOcclusionCuller.GridLayout grid = XenoOctreeForest.this.grid;
                RenderSection[] gridSections = XenoOctreeForest.this.ctxGridSections;
                int centerSecX = XenoOctreeForest.this.centerSecX;
                int centerSecZ = XenoOctreeForest.this.centerSecZ;
                XenoBranchlessFrustum bf = XenoOctreeForest.this.ctxBf;
                Frustum frustum = XenoOctreeForest.this.ctxFrustum;
                float bfCamX = XenoOctreeForest.this.ctxBfCamX;
                float bfCamY = XenoOctreeForest.this.ctxBfCamY;
                float bfCamZ = XenoOctreeForest.this.ctxBfCamZ;
                int camBlockX = XenoOctreeForest.this.ctxCamBlockX;
                int camBlockY = XenoOctreeForest.this.ctxCamBlockY;
                int camBlockZ = XenoOctreeForest.this.ctxCamBlockZ;
                List<RenderSection> outVisible = XenoOctreeForest.this.ctxOutVisible;
                List<RenderSection> outNearby = XenoOctreeForest.this.ctxOutNearby;

                for (int c = 0; c < 8; c++) {
                    int octant = c ^ orderMod;
                    if ((leafMask & (1 << octant)) == 0) {
                        continue;
                    }
                    int worldSecX = this.originSecX + baseX + (octant & 1);
                    int worldSecY = this.originSecY + baseY + ((octant >>> 1) & 1);
                    int worldSecZ = this.originSecZ + baseZ + ((octant >>> 2) & 1);
                    int idx = grid.indexFor(worldSecX, worldSecY, worldSecZ, centerSecX, centerSecZ);
                    if (idx < 0) {
                        continue;
                    }
                    RenderSection section = gridSections[idx];
                    if (section == null || section.getSectionNode() != SectionPos.asLong(worldSecX, worldSecY, worldSecZ)) {
                        continue;
                    }

                    BlockPos origin = section.getRenderOrigin();
                    int ox = origin.getX();
                    int oy = origin.getY();
                    int oz = origin.getZ();

                    if (!parentInside) {
                        boolean visible;
                        if (bf != null) {
                            SectionMesh mesh = section.getSectionMesh();
                            if (mesh instanceof XenoCompiledSectionMeshExtension ext && ext.xeno$hasTightBounds()) {
                                visible = bf.isVisibleRel(
                                        ox + ext.xeno$minX() - bfCamX,
                                        oy + ext.xeno$minY() - bfCamY,
                                        oz + ext.xeno$minZ() - bfCamZ,
                                        ox + ext.xeno$maxX() - bfCamX,
                                        oy + ext.xeno$maxY() - bfCamY,
                                        oz + ext.xeno$maxZ() - bfCamZ
                                );
                            } else {
                                visible = bf.isSectionVisibleRel(ox + 8.0f - bfCamX, oy + 8.0f - bfCamY, oz + 8.0f - bfCamZ);
                            }
                        } else {
                            visible = frustum.isVisible(section.getBoundingBox());
                        }
                        if (!visible) {
                            continue;
                        }
                    }

                    if (outVisible != null) {
                        outVisible.add(section);
                    }
                    if (outNearby != null
                            && camBlockX > ox - NEARBY_PADDING && camBlockX < ox + 16 + NEARBY_PADDING
                            && camBlockY > oy - NEARBY_PADDING && camBlockY < oy + 16 + NEARBY_PADDING
                            && camBlockZ > oz - NEARBY_PADDING && camBlockZ < oz + 16 + NEARBY_PADDING) {
                        outNearby.add(section);
                    }
                }
                return;
            }

            int mortonShift = level * 3;
            int childBlockSize = 16 << level;
            XenoBranchlessFrustum bf = XenoOctreeForest.this.ctxBf;
            Frustum frustum = XenoOctreeForest.this.ctxFrustum;

            for (int c = 0; c < 8; c++) {
                int octant = c ^ orderMod;
                if (oddLevel) {
                    long byteMask = 0xFFL << (octant << 3);
                    if ((summaryWord & byteMask) == 0L) {
                        continue;
                    }
                } else {
                    int bitPos = bitBase | octant;
                    if ((summaryWord & (1L << bitPos)) == 0L) {
                        continue;
                    }
                }

                int childSecX = baseX + ((octant & 1) << level);
                int childSecY = baseY + (((octant >>> 1) & 1) << level);
                int childSecZ = baseZ + (((octant >>> 2) & 1) << level);

                boolean childInside = parentInside;
                if (!childInside) {
                    double minX = (this.originSecX + childSecX) << 4;
                    double minY = (this.originSecY + childSecY) << 4;
                    double minZ = (this.originSecZ + childSecZ) << 4;
                    int vis = bf != null
                            ? bf.cubeInFrustum(minX, minY, minZ, minX + childBlockSize, minY + childBlockSize, minZ + childBlockSize)
                            : (frustum.isVisible(new AABB(minX, minY, minZ, minX + childBlockSize, minY + childBlockSize, minZ + childBlockSize))
                            ? XenoBranchlessFrustum.INTERSECT
                            : XenoBranchlessFrustum.OUTSIDE);
                    if (vis == XenoBranchlessFrustum.OUTSIDE) {
                        continue;
                    }
                    childInside = vis == XenoBranchlessFrustum.INSIDE;
                }

                this.visitNode(
                        level - 1,
                        mortonBase | (octant << mortonShift),
                        childSecX,
                        childSecY,
                        childSecZ,
                        childInside
                );
            }
        }
    }
}
