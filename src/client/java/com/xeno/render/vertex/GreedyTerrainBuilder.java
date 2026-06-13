package com.xeno.render.vertex;

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


import java.nio.ByteBuffer;
import net.minecraft.world.level.block.state.BlockState;
import com.xeno.render.chunk.build.GreedyMeshBuilder;
import com.xeno.render.chunk.cull.QuadFacing;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

/**
 * TerrainBuilder variant that captures quads for greedy meshing.
 * Overrides setBlockAttributes to track the current block state,
 * and intercepts vertex writes via GreedyCaptureBuffer.
 */
public class GreedyTerrainBuilder extends TerrainBuilder {
    private static final Logger LOGGER = LogManager.getLogger("xeno-greedy");
    final TerrainBuilder real;
    final GreedyMeshBuilder greedyBuilder;
    final GreedyCaptureBuffer[] captures;
    final int[] quadVertexCounts;
    BlockState currentBlockState;

    private final long tempRawAddr;
    private static final VertexBuilder COMPRESSED_VB = new VertexBuilder.CompressedVertexBuilder();

    int totalVerticesCaptured;
    int totalQuadsCaptured;

    public GreedyTerrainBuilder(TerrainBuilder real, int size, VertexBuilder vb) {
        super(size, vb);
        this.real = real;
        this.greedyBuilder = new GreedyMeshBuilder();
        this.captures = new GreedyCaptureBuffer[QuadFacing.COUNT];
        this.quadVertexCounts = new int[QuadFacing.COUNT];
        this.tempRawAddr = MemoryUtil.nmemAlloc(64);
        for (int i = 0; i < QuadFacing.COUNT; i++) {
            captures[i] = new GreedyCaptureBuffer(this, i);
        }
        LOGGER.debug("GreedyTerrainBuilder created, real builder class={}", real.getClass().getSimpleName());
    }

    @Override
    public TerrainBufferBuilder getBufferBuilder(int i) {
        return captures[i];
    }

    @Override
    public void setBlockAttributes(BlockState state) {
        this.currentBlockState = state;
        real.setBlockAttributes(state);
    }

    void onVertex(int fi, long ptr) {
        int off = quadVertexCounts[fi] * 16;
        MemoryUtil.memCopy(ptr, tempRawAddr + off, 16);
        quadVertexCounts[fi]++;
        totalVerticesCaptured++;
        if (quadVertexCounts[fi] == 4) {
            decodeAndCapture(fi);
            quadVertexCounts[fi] = 0;
        }
    }

    private void decodeAndCapture(int fi) {
        long addr = tempRawAddr;
        short sx = MemoryUtil.memGetShort(addr);
        short sy = MemoryUtil.memGetShort(addr + 2);
        short sz = MemoryUtil.memGetShort(addr + 4);
        short sl0 = MemoryUtil.memGetShort(addr + 6);   int c0 = MemoryUtil.memGetInt(addr + 12);
        short sl1 = MemoryUtil.memGetShort(addr + 22);  int c1 = MemoryUtil.memGetInt(addr + 28);
        short sl2 = MemoryUtil.memGetShort(addr + 38);  int c2 = MemoryUtil.memGetInt(addr + 44);
        short sl3 = MemoryUtil.memGetShort(addr + 54);  int c3 = MemoryUtil.memGetInt(addr + 60);

        float fx = (sx + 8192f) / 2048f;
        float fy = (sy + 8192f) / 2048f;
        float fz = (sz + 8192f) / 2048f;
        int lx = Math.round(fx), ly = Math.round(fy), lz = Math.round(fz);

        int l0 = ((sl0 & 0xFF00) << 8) | (sl0 & 0xFF);
        int l1 = ((sl1 & 0xFF00) << 8) | (sl1 & 0xFF);
        int l2 = ((sl2 & 0xFF00) << 8) | (sl2 & 0xFF);
        int l3 = ((sl3 & 0xFF00) << 8) | (sl3 & 0xFF);

        QuadFacing facing = QuadFacing.VALUES[fi];
        TerrainRenderType rt = (fi == QuadFacing.UNDEFINED.ordinal())
            ? TerrainRenderType.TRANSLUCENT : TerrainRenderType.SOLID;

        int bsId = 0;
        if (currentBlockState != null) {
            bsId = currentBlockState.getBlock().hashCode();
        }

        totalQuadsCaptured++;
        greedyBuilder.captureFace(lx, ly, lz, facing, rt, bsId, c0, c1, c2, c3, l0, l1, l2, l3);
    }

    @Override
    public DrawState endDrawing() {
        LOGGER.debug("endDrawing: verticesCaptured={} quadsCaptured={}", totalVerticesCaptured, totalQuadsCaptured);

        int merged = greedyBuilder.mergeAndEmitDirect(real);

        // Re-setup quad sorting points on real builder now that vertices are written
        int undefVerts = real.getBufferBuilder(QuadFacing.UNDEFINED.ordinal()).getVertices();
        LOGGER.debug("endDrawing: merged={} undefVertsAfterMerge={}", merged, undefVerts);
        if (undefVerts > 0) {
            real.setupQuadSortingPoints();
        }

        DrawState state = real.endDrawing();
        LOGGER.debug("endDrawing: DrawState(indexCount={}, sequential={})", state.indexCount(), state.sequentialIndex());

        // Verify real builder has vertex data
        int totalVerts = 0;
        for (int i = 0; i < QuadFacing.COUNT; i++) {
            int v = real.getBufferBuilder(i).getVertices();
            if (v > 0) LOGGER.debug("  facing[{}] vertices={}", i, v);
            totalVerts += v;
        }
        LOGGER.debug("endDrawing: total real vertices={}", totalVerts);
        return state;
    }

    @Override
    public ByteBuffer getIndexBuffer() { return real.getIndexBuffer(); }

    @Override
    public void setupQuadSorting(float x, float y, float z) { real.setupQuadSorting(x, y, z); }

    @Override
    public QuadSorter.SortState getSortState() { return real.getSortState(); }

    @Override
    public void clear() {
        real.clear();
        Arrays.fill(quadVertexCounts, 0);
    }

    static class GreedyCaptureBuffer extends TerrainBufferBuilder {
        private final GreedyTerrainBuilder parent;
        private final int facingOrd;
        private final long tempPtr;

        GreedyCaptureBuffer(GreedyTerrainBuilder p, int fi) {
            super(64, 16, new VertexBuilder.CompressedVertexBuilder());
            this.parent = p;
            this.facingOrd = fi;
            this.tempPtr = MemoryUtil.nmemAlloc(64);
        }

        @Override
        public void ensureCapacity() {}

        @Override
        public void vertex(float x, float y, float z, int color, float u, float v, int light, int n) {
            COMPRESSED_VB.vertex(tempPtr, x, y, z, color, u, v, light, n);
            parent.onVertex(facingOrd, tempPtr);
        }

        @Override
        public int getVertices() { return 0; }

        @Override
        public long getPtr() { return 0; }

        @Override
        public ByteBuffer getBuffer() { return null; }
    }
}
