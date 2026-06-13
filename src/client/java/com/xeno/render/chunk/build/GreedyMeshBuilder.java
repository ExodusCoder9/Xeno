package com.xeno.render.chunk.build;

/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */


import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.vertex.TerrainBufferBuilder;
import com.xeno.render.vertex.TerrainBuilder;
import com.xeno.render.vertex.TerrainRenderType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Binary greedy meshing builder. Uses bitmasks for 16×16 planes
 * to rapidly merge adjacent coplanar faces of the same block type.
 */
public class GreedyMeshBuilder {
    private static final Logger LOGGER = LogManager.getLogger("xeno-greedy");
    private static final int SIZE = 16;
    private static int totalCaptures = 0;
    private static int totalMerged = 0;
    private static int totalUndefined = 0;

    private static class FaceCell {
        int blockStateId;
        int renderTypeOrd;
        int c0, c1, c2, c3;
        int l0, l1, l2, l3;
        boolean present;
    }

    private final FaceCell[][][][] grid = new FaceCell[SIZE][SIZE][SIZE][QuadFacing.COUNT];

    public GreedyMeshBuilder() {
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < SIZE; y++)
                for (int z = 0; z < SIZE; z++)
                    for (int f = 0; f < QuadFacing.COUNT; f++)
                        grid[x][y][z][f] = new FaceCell();
    }

    public void captureFace(
        int x, int y, int z, QuadFacing facing, TerrainRenderType rt, int bsId,
        int c0, int c1, int c2, int c3, int l0, int l1, int l2, int l3
    ) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            LOGGER.warn("captureFace out of bounds: ({},{},{}) facing={} rt={}", x, y, z, facing, rt);
            return;
        }
        FaceCell cell = grid[x][y][z][facing.ordinal()];
        cell.blockStateId = bsId;
        cell.renderTypeOrd = rt.ordinal();
        cell.c0 = c0; cell.c1 = c1; cell.c2 = c2; cell.c3 = c3;
        cell.l0 = l0; cell.l1 = l1; cell.l2 = l2; cell.l3 = l3;
        cell.present = true;
        totalCaptures++;
    }

    public void clear() {
        for (int x = 0; x < SIZE; x++)
            for (int y = 0; y < SIZE; y++)
                for (int z = 0; z < SIZE; z++)
                    for (int f = 0; f < QuadFacing.COUNT; f++)
                        grid[x][y][z][f].present = false;
    }

    /** Merge and emit directly into a single TerrainBuilder. */
    public int mergeAndEmitDirect(TerrainBuilder target) {
        int beforeCaptures = totalCaptures;
        int emitted = 0;
        for (int fi : new int[]{0, 1, 2, 3, 4, 6}) {
            int fe = mergeFacingDirect(target, fi);
            emitted += fe;
            if (fe > 0) LOGGER.debug("  Facing {}: {} merged quads emitted", QuadFacing.VALUES[fi], fe);
        }
        int ue = emitUndefinedDirect(target);
        emitted += ue;
        if (ue > 0) LOGGER.debug("  Undefined: {} singles emitted", ue);

        // Count actual vertices in real builder
        int totalVerts = 0;
        for (int i = 0; i < QuadFacing.COUNT; i++) {
            totalVerts += target.getBufferBuilder(i).getVertices();
        }

        LOGGER.debug("mergeAndEmitDirect: captured={} emitted={} realVertices={}",
            totalCaptures, emitted, totalVerts);
        totalMerged += emitted;
        totalUndefined += ue;
        return emitted;
    }

    // === merge for single TerrainBuilder ===

    private int mergeFacingDirect(TerrainBuilder target, int fi) {
        QuadFacing f = QuadFacing.VALUES[fi];
        int pa, au, av;
        switch (f) { case X_POS: case X_NEG: pa=0; au=1; av=2; break; case Y_POS: case Y_NEG: pa=1; au=0; av=2; break; default: pa=2; au=0; av=1; }
        int emitted = 0;
        int planesWithFaces = 0;
        for (int plane = 0; plane < SIZE; plane++) {
            Map<Long, List<int[]>> groups = new HashMap<>();
            Map<Long, FaceCell> samples = new HashMap<>();
            for (int u = 0; u < SIZE; u++) for (int v = 0; v < SIZE; v++) {
                int x = pa==0?plane : au==0?u:v, y = pa==1?plane : au==1?u:v, z = pa==2?plane : au==2?u:v;
                FaceCell c = grid[x][y][z][fi]; if (!c.present) continue;
                long k = ((long)c.blockStateId << 32) | (c.renderTypeOrd & 0xFFFF_FFFFL);
                groups.computeIfAbsent(k, kk -> new ArrayList<>()).add(new int[]{u, v});
                samples.putIfAbsent(k, c);
            }
            if (!groups.isEmpty()) planesWithFaces++;
            for (Map.Entry<Long, List<int[]>> e : groups.entrySet()) {
                FaceCell s = samples.get(e.getKey());
                long[] m = new long[SIZE];
                for (int[] uv : e.getValue()) m[uv[1]] |= (1L << uv[0]);
                boolean[][] vis = new boolean[SIZE][SIZE];
                for (int v = 0; v < SIZE; v++) {
                    long row = m[v];
                    while (row != 0) {
                        int u = Long.numberOfTrailingZeros(row);
                        if (vis[u][v]) { row &= ~(1L << u); continue; }
                        int eu = u; long t = row >>> (u+1);
                        while ((t & 1L) != 0 && eu+1 < SIZE && !vis[eu+1][v]) { eu++; t>>>=1; }
                        int ev = v;
                        outer: for (int nv = v+1; nv < SIZE; nv++) {
                            long nr = m[nv];
                            for (int nu = u; nu <= eu; nu++) if (((nr>>>nu)&1L)==0 || vis[nu][nv]) break outer;
                            ev = nv;
                        }
                        for (int nu = u; nu <= eu; nu++) for (int nv = v; nv <= ev; nv++) { vis[nu][nv]=true; m[nv] &= ~(1L << nu); }
                        int w = eu-u+1, h = ev-v+1;
                        emitMergedQuad(target.getBufferBuilder(fi), w, h, pa, au, av, plane, u, v, s);
                        emitted++;
                    }
                }
            }
        }
        if (emitted > 0) LOGGER.debug("  mergeFacing {}: planesWithFaces={} merged={}", f, planesWithFaces, emitted);
        return emitted;
    }

    private int emitUndefinedDirect(TerrainBuilder target) {
        int emitted = 0, uf = QuadFacing.UNDEFINED.ordinal();
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < SIZE; y++) for (int z = 0; z < SIZE; z++) {
            FaceCell c = grid[x][y][z][uf]; if (!c.present) continue;
            emitSingleQuad(target.getBufferBuilder(uf), x, y, z, c);
            emitted++;
        }
        return emitted;
    }

    // === quad emission helpers ===

    private void emitSingleQuad(TerrainBufferBuilder buf, int x, int y, int z, FaceCell c) {
        buf.ensureCapacity();
        buf.vertex(x+0, y+0, z+0, c.c0, 0f, 0f, c.l0, 0);
        buf.vertex(x+1, y+0, z+0, c.c1, 1f, 0f, c.l1, 0);
        buf.vertex(x+1, y+1, z+1, c.c2, 1f, 1f, c.l2, 0);
        buf.vertex(x+0, y+1, z+1, c.c3, 0f, 1f, c.l3, 0);
    }

    private void emitMergedQuad(TerrainBufferBuilder buf, int w, int h,
        int pa, int au, int av, int plane, int u, int v, FaceCell c) {
        buf.ensureCapacity();
        float x0=0,y0=0,z0=0,x1=0,y1=0,z1=0;
        if (pa==0) { x0=x1=plane; y0=u; z0=v; y1=u+h; z1=v+w; }
        else if (pa==1) { y0=y1=plane; x0=u; z0=v; x1=u+w; z1=v+h; }
        else { z0=z1=plane; x0=u; y0=v; x1=u+w; y1=v+h; }
        float uS = w, vS = h;
        buf.vertex(x0, y0, z0, c.c0, 0f, 0f, c.l0, 0);
        buf.vertex(x1, y0, z0, c.c1, uS, 0f, c.l1, 0);
        buf.vertex(x1, y1, z1, c.c2, uS, vS, c.l2, 0);
        buf.vertex(x0, y1, z1, c.c3, 0f, vS, c.l3, 0);
    }
}
