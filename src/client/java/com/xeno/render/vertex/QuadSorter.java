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


import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import com.xeno.render.util.SortUtil;
import com.xeno.vulkan.util.MemoryAccess;
import org.joml.Vector3f;

public class QuadSorter {
   private Vector3f[] sortingPoints;
   private float sortX = Float.NaN;
   private float sortY = Float.NaN;
   private float sortZ = Float.NaN;
   private boolean indexOnly;
   private VertexFormat format;
   private int vertexCount;
   private int indexCount;
   private float[] distances;
   private int[] sortingPointsIndices;

   public QuadSorter() {
   }

   public void setQuadSortOrigin(float x, float y, float z) {
      this.sortX = x;
      this.sortY = y;
      this.sortZ = z;
   }

   public QuadSorter.SortState getSortState() {
      return new QuadSorter.SortState(this.vertexCount, this.sortingPoints, this.distances, this.sortingPointsIndices);
   }

   public void restoreSortState(QuadSorter.SortState sortState) {
      this.vertexCount = sortState.vertexCount;
      this.sortingPoints = sortState.sortingPoints;
      this.distances = sortState.distances;
      this.sortingPointsIndices = sortState.sortingPointsIndices;
      this.indexOnly = true;
   }

   public void setupQuadSortingPoints(long bufferPtr, int vertexCount, VertexFormat format) {
      this.vertexCount = vertexCount;
      int pointCount = vertexCount / 4;
      Vector3f[] sortingPoints = new Vector3f[pointCount];
      int vertexSize = format.getVertexSize();
      int quadStride = vertexSize * 4;
      int offset = vertexSize * 2;
      if (format == CustomVertexFormat.COMPRESSED_TERRAIN) {
         float invConv = 4.8828125E-4F;
         float convOffset = 4.0F;

         for (int m = 0; m < pointCount; m++) {
            long ptr = bufferPtr + (long)m * quadStride;
            short x0 = MemoryAccess.memGetShort(ptr + 0L);
            short y0 = MemoryAccess.memGetShort(ptr + 2L);
            short z0 = MemoryAccess.memGetShort(ptr + 4L);
            short x2 = MemoryAccess.memGetShort(ptr + offset + 0L);
            short y2 = MemoryAccess.memGetShort(ptr + offset + 2L);
            short z2 = MemoryAccess.memGetShort(ptr + offset + 4L);
            float xa = (x0 + x2) * 4.8828125E-4F * 0.5F + 4.0F;
            float ya = (y0 + y2) * 4.8828125E-4F * 0.5F + 4.0F;
            float za = (z0 + z2) * 4.8828125E-4F * 0.5F + 4.0F;
            sortingPoints[m] = new Vector3f(xa, ya, za);
         }
      } else {
         for (int m = 0; m < pointCount; m++) {
            long ptr = bufferPtr + (long)m * quadStride;
            float x0 = MemoryAccess.memGetFloat(ptr + 0L);
            float y0 = MemoryAccess.memGetFloat(ptr + 4L);
            float z0 = MemoryAccess.memGetFloat(ptr + 8L);
            float x2 = MemoryAccess.memGetFloat(ptr + offset + 0L);
            float y2 = MemoryAccess.memGetFloat(ptr + offset + 4L);
            float z2 = MemoryAccess.memGetFloat(ptr + offset + 8L);
            float q = (x0 + x2) * 0.5F;
            float r = (y0 + y2) * 0.5F;
            float s = (z0 + z2) * 0.5F;
            sortingPoints[m] = new Vector3f(q, r, s);
         }
      }

      this.sortingPoints = sortingPoints;
      this.distances = new float[pointCount];
      this.sortingPointsIndices = new int[pointCount];
   }

   public void putSortedQuadIndices(TerrainBufferBuilder bufferBuilder, IndexType indexType) {
      float[] distances = this.distances;
      int[] sortingPointsIndices = this.sortingPointsIndices;

      for (int i = 0; i < this.sortingPoints.length; sortingPointsIndices[i] = i++) {
         float dx = this.sortingPoints[i].x() - this.sortX;
         float dy = this.sortingPoints[i].y() - this.sortY;
         float dz = this.sortingPoints[i].z() - this.sortZ;
         distances[i] = dx * dx + dy * dy + dz * dz;
      }

      SortUtil.mergeSort(sortingPointsIndices, distances);
      long ptr = bufferBuilder.getPtr();
      int size = indexType.bytes;
      int stride = 4;

      for (int i = 0; i < sortingPointsIndices.length; i++) {
         int quadIndex = sortingPointsIndices[i];
         int baseVertex = quadIndex * 4;
         MemoryAccess.memPutInt(ptr + size * 0L, baseVertex + 0);
         MemoryAccess.memPutInt(ptr + size * 1L, baseVertex + 1);
         MemoryAccess.memPutInt(ptr + size * 2L, baseVertex + 2);
         MemoryAccess.memPutInt(ptr + size * 3L, baseVertex + 2);
         MemoryAccess.memPutInt(ptr + size * 4L, baseVertex + 3);
         MemoryAccess.memPutInt(ptr + size * 5L, baseVertex + 0);
         ptr += size * 6L;
      }
   }

   public void putSortedQuadIndices(TerrainBuilder bufferBuilder, IndexType indexType) {
      float[] distances = new float[this.sortingPoints.length];
      int[] sortingPoints = new int[this.sortingPoints.length];

      for (int i = 0; i < this.sortingPoints.length; sortingPoints[i] = i++) {
         float dx = this.sortingPoints[i].x() - this.sortX;
         float dy = this.sortingPoints[i].y() - this.sortY;
         float dz = this.sortingPoints[i].z() - this.sortZ;
         distances[i] = dx * dx + dy * dy + dz * dz;
      }

      SortUtil.mergeSort(sortingPoints, distances);
      long ptr = bufferBuilder.indexBufferPtr;
      int size = indexType.bytes;
      int stride = 4;

      for (int i = 0; i < sortingPoints.length; i++) {
         int quadIndex = sortingPoints[i];
         int baseVertex = quadIndex * 4;
         MemoryAccess.memPutInt(ptr + size * 0L, baseVertex + 0);
         MemoryAccess.memPutInt(ptr + size * 1L, baseVertex + 1);
         MemoryAccess.memPutInt(ptr + size * 2L, baseVertex + 2);
         MemoryAccess.memPutInt(ptr + size * 3L, baseVertex + 2);
         MemoryAccess.memPutInt(ptr + size * 4L, baseVertex + 3);
         MemoryAccess.memPutInt(ptr + size * 5L, baseVertex + 0);
         ptr += size * 6L;
      }
   }

   public void reset() {
      this.vertexCount = 0;
   }

   public int getVertexCount() {
      return this.vertexCount;
   }

   public int getIndexCount() {
      return this.indexCount;
   }

   public static class SortState {
      final int vertexCount;
      final Vector3f[] sortingPoints;
      final float[] distances;
      final int[] sortingPointsIndices;

      SortState(int vertexCount, Vector3f[] sortingPoints, float[] distances, int[] sortingPointsIndices) {
         this.vertexCount = vertexCount;
         this.sortingPoints = sortingPoints;
         this.distances = distances;
         this.sortingPointsIndices = sortingPointsIndices;
      }
   }
}
