package com.xeno.render.chunk;

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


import java.util.EnumMap;
import net.minecraft.util.Mth;
import com.xeno.render.chunk.buffer.AreaBuffer;
import com.xeno.render.chunk.buffer.DrawBuffers;
import com.xeno.render.chunk.frustum.FrustumOctree;
import com.xeno.render.chunk.frustum.VFrustum;
import com.xeno.render.chunk.util.CircularIntList;
import com.xeno.render.chunk.util.Util;
import com.xeno.render.vertex.TerrainRenderType;
import org.joml.Vector3i;

public class ChunkAreaManager {
   public static final int WIDTH = 8;
   public static final int HEIGHT = 8;
   public static final int AREA_SIZE = 512;
   public static final int AREA_SH_XZ = Util.flooredLog(8);
   public static final int AREA_SH_Y = Util.flooredLog(8);
   public static final int SEC_SH = 4;
   public static final int BLOCK_TO_AREA_SH_XZ = AREA_SH_XZ + 4;
   public static final int BLOCK_TO_AREA_SH_Y = AREA_SH_Y + 4;
   public final int size;
   final int sectionGridWidth;
   final int xzSize;
   final int ySize;
   final int minHeight;
   final ChunkArea[] chunkAreasArr;
   int prevX;
   int prevZ;
   private final CircularIntList xList;
   private final CircularIntList zList;
   private final CircularIntList.RangeIterator xComplIterator;

   public ChunkAreaManager(int width, int height, int minHeight) {
      this.minHeight = minHeight;
      this.sectionGridWidth = width;
      int t = (width >> AREA_SH_XZ) + 2;
      int relativeHeight = height - (minHeight >> 4);
      this.ySize = (relativeHeight & 5) == 0 ? relativeHeight >> AREA_SH_Y : (relativeHeight >> AREA_SH_Y) + 1;
      if ((t & 1) == 0) {
         t++;
      }

      this.xzSize = t;
      this.size = this.xzSize * this.ySize * this.xzSize;
      this.chunkAreasArr = new ChunkArea[this.size];

      for (int z = 0; z < this.xzSize; z++) {
         for (int y = 0; y < this.ySize; y++) {
            for (int x = 0; x < this.xzSize; x++) {
               int idx = this.getAreaIndex(x, y, z);
               Vector3i origin = new Vector3i(x << BLOCK_TO_AREA_SH_XZ, y << BLOCK_TO_AREA_SH_Y, z << BLOCK_TO_AREA_SH_XZ);
               this.chunkAreasArr[idx] = new ChunkArea(idx, origin, minHeight);
            }
         }
      }

      this.prevX = Integer.MIN_VALUE;
      this.prevZ = Integer.MIN_VALUE;
      this.xList = new CircularIntList(this.xzSize);
      this.zList = new CircularIntList(this.xzSize);
      this.xComplIterator = this.xList.createRangeIterator();
   }

   public void repositionAreas(int secX, int secZ) {
      int xS = secX >> AREA_SH_XZ;
      int zS = secZ >> AREA_SH_XZ;
      int deltaX = Mth.clamp(xS - this.prevX, -this.xzSize, this.xzSize);
      int deltaZ = Mth.clamp(zS - this.prevZ, -this.xzSize, this.xzSize);
      int xAbsChunkIndex = xS - this.xzSize / 2;
      int xStart = Math.floorMod(xAbsChunkIndex, this.xzSize);
      int zAbsChunkIndex = zS - this.xzSize / 2;
      int zStart = Math.floorMod(zAbsChunkIndex, this.xzSize);
      CircularIntList xList = this.xList;
      CircularIntList zList = this.zList;
      xList.updateStartIdx(xStart);
      zList.updateStartIdx(zStart);
      CircularIntList.OwnIterator xIterator = xList.iterator();
      CircularIntList.OwnIterator zIterator = zList.iterator();
      int xRangeStart;
      int xRangeEnd;
      int xComplStart;
      int xComplEnd;
      if (deltaX >= 0) {
         xRangeStart = this.xzSize - deltaX;
         xRangeEnd = this.xzSize - 1;
         xComplStart = 0;
         xComplEnd = xRangeStart - 1;
      } else {
         xRangeStart = 0;
         xRangeEnd = -deltaX - 1;
         xComplStart = xRangeEnd;
         xComplEnd = this.xzSize - 1;
      }

      int zRangeStart;
      int zRangeEnd;
      if (deltaZ >= 0) {
         zRangeStart = this.xzSize - deltaZ;
         zRangeEnd = this.xzSize - 1;
      } else {
         zRangeStart = 0;
         zRangeEnd = -deltaZ - 1;
      }

      CircularIntList.RangeIterator xRangeIterator = xList.getRangeIterator(xRangeStart, xRangeEnd);
      CircularIntList.RangeIterator zRangeIterator = zList.getRangeIterator(zRangeStart, zRangeEnd);
      CircularIntList.RangeIterator xComplIterator = this.xComplIterator;
      xComplIterator.update(xComplStart, xComplEnd);

      for (int var29 = xS - this.xzSize / 2 + xRangeStart; xRangeIterator.hasNext(); var29++) {
         int xRelativeIndex = xRangeIterator.next();
         int x1 = var29 << AREA_SH_XZ + 4;
         zIterator.restart();

         for (int var31 = zS - (this.xzSize >> 1); zIterator.hasNext(); var31++) {
            int zRelativeIndex = zIterator.next();
            int z1 = var31 << AREA_SH_XZ + 4;

            for (int yRel = 0; yRel < this.ySize; yRel++) {
               this.moveArea(xRelativeIndex, yRel, zRelativeIndex, x1, z1);
            }
         }
      }

      for (int var30 = xS - this.xzSize / 2 + xComplStart; xComplIterator.hasNext(); var30++) {
         int xRelativeIndex = xComplIterator.next();
         int x1 = var30 << AREA_SH_XZ + 4;
         zRangeIterator.restart();

         for (int var32 = zS - (this.xzSize >> 1) + zRangeStart; zRangeIterator.hasNext(); var32++) {
            int zRelativeIndex = zRangeIterator.next();
            int z1 = var32 << AREA_SH_XZ + 4;

            for (int yRel = 0; yRel < this.ySize; yRel++) {
               this.moveArea(xRelativeIndex, yRel, zRelativeIndex, x1, z1);
            }
         }
      }

      this.prevX = xS;
      this.prevZ = zS;
   }

   private void moveArea(int xRelativeIndex, int yRel, int zRelativeIndex, int x1, int z1) {
      int y1 = this.minHeight + (yRel << AREA_SH_Y + 4);
      ChunkArea chunkArea = this.chunkAreasArr[this.getAreaIndex(xRelativeIndex, yRel, zRelativeIndex)];
      chunkArea.setPosition(x1, y1, z1);
      chunkArea.releaseBuffers();
   }

   public ChunkArea getChunkArea(RenderSection section, int x, int y, int z) {
      int shX = AREA_SH_XZ + 4;
      int shY = AREA_SH_Y + 4;
      int shZ = AREA_SH_XZ + 4;
      int AreaX = x >> shX;
      int AreaY = y - this.minHeight >> shY;
      int AreaZ = z >> shZ;
      int x1 = Math.floorMod(AreaX, this.xzSize);
      int z1 = Math.floorMod(AreaZ, this.xzSize);
      return this.chunkAreasArr[this.getAreaIndex(x1, AreaY, z1)];
   }

   public ChunkArea getChunkArea(int idx) {
      return idx >= 0 && idx < this.chunkAreasArr.length ? this.chunkAreasArr[idx] : null;
   }

   public void updateFrustumVisibility(VFrustum frustum) {
      FrustumOctree.updateFrustumVisibility(frustum, this.chunkAreasArr);
   }

   public void resetQueues() {
      for (ChunkArea chunkArea : this.chunkAreasArr) {
         chunkArea.resetQueue();
      }
   }

   private int getAreaIndex(int x, int y, int z) {
      return (z * this.ySize + y) * this.xzSize + x;
   }

   public void freeAllBuffers() {
      for (ChunkArea chunkArea : this.chunkAreasArr) {
         chunkArea.free();
      }
   }

   public String[] getStats() {
      long vbSize = 0L;
      long ibSize = 0L;
      long frag = 0L;
      long vbUsed = 0L;
      long ibUsed = 0L;
      int count = 0;

      for (ChunkArea chunkArea : this.chunkAreasArr) {
         DrawBuffers drawBuffers = chunkArea.drawBuffers;
         if (drawBuffers.isAllocated()) {
            EnumMap<TerrainRenderType, AreaBuffer> vertexBuffers = drawBuffers.getVertexBuffers();

            for (AreaBuffer buffer : vertexBuffers.values()) {
               vbSize += buffer.getSize();
               vbUsed += buffer.getUsed();
               frag += buffer.fragmentation();
            }

            AreaBuffer indexBuffer = drawBuffers.getIndexBuffer();
            if (indexBuffer != null) {
               ibSize += indexBuffer.getSize();
               ibUsed += indexBuffer.getUsed();
               frag += indexBuffer.fragmentation();
            }

            count++;
         }
      }

      int div = 1048576;
      vbSize /= 1048576L;
      vbUsed /= 1048576L;
      ibSize /= 1048576L;
      ibUsed /= 1048576L;
      frag /= 1048576L;
      return new String[]{
         String.format("Vertex Buffers: %d/%d MB", vbUsed, vbSize),
         String.format("Index Buffers: %d/%d MB", ibUsed, ibSize),
         String.format("Allocations: %d Frag: %d MB", count, frag)
      };
   }
}
