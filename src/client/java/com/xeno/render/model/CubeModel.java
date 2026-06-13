package com.xeno.render.model;

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


import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class CubeModel {
   private CubeModel.Polygon[] polygons;
   public float minX;
   public float minY;
   public float minZ;
   public float maxX;
   public float maxY;
   public float maxZ;
   CubeModel.Vertex[] vertices;

   public CubeModel() {
   }

   public void setVertices(
      int u,
      int v,
      float minX,
      float minY,
      float minZ,
      float dimX,
      float dimY,
      float dimZ,
      float growX,
      float growY,
      float growZ,
      boolean mirror,
      float uTexScale,
      float vTexScale,
      Set<Direction> set
   ) {
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
      this.maxX = minX + dimX;
      this.maxY = minY + dimY;
      this.maxZ = minZ + dimZ;
      this.polygons = new CubeModel.Polygon[set.size()];
      float s = this.maxX;
      float t = this.maxY;
      float u1 = this.maxZ;
      minX -= growX;
      minY -= growY;
      minZ -= growZ;
      s += growX;
      t += growY;
      u1 += growZ;
      if (mirror) {
         float v1 = s;
         s = minX;
         minX = v1;
      }

      this.vertices = new CubeModel.Vertex[]{
         new CubeModel.Vertex(minX, minY, minZ, 0.0F, 0.0F),
         new CubeModel.Vertex(s, minY, minZ, 0.0F, 8.0F),
         new CubeModel.Vertex(s, t, minZ, 8.0F, 8.0F),
         new CubeModel.Vertex(minX, t, minZ, 8.0F, 0.0F),
         new CubeModel.Vertex(minX, minY, u1, 0.0F, 0.0F),
         new CubeModel.Vertex(s, minY, u1, 0.0F, 8.0F),
         new CubeModel.Vertex(s, t, u1, 8.0F, 8.0F),
         new CubeModel.Vertex(minX, t, u1, 8.0F, 0.0F)
      };
      float w = u;
      float x = u + dimZ;
      float y = u + dimZ + dimX;
      float z = u + dimZ + dimX + dimX;
      float aa = u + dimZ + dimX + dimZ;
      float ab = u + dimZ + dimX + dimZ + dimX;
      float ac = v;
      float ad = v + dimZ;
      float ae = v + dimZ + dimY;
      CubeModel.Vertex vertex1 = this.vertices[0];
      CubeModel.Vertex vertex2 = this.vertices[1];
      CubeModel.Vertex vertex3 = this.vertices[2];
      CubeModel.Vertex vertex4 = this.vertices[3];
      CubeModel.Vertex vertex5 = this.vertices[4];
      CubeModel.Vertex vertex6 = this.vertices[5];
      CubeModel.Vertex vertex7 = this.vertices[6];
      CubeModel.Vertex vertex8 = this.vertices[7];
      int idx = 0;
      if (set.contains(Direction.DOWN)) {
         this.polygons[idx++] = new CubeModel.Polygon(
            new CubeModel.Vertex[]{vertex6, vertex5, vertex1, vertex2}, x, ac, y, ad, uTexScale, vTexScale, mirror, Direction.DOWN
         );
      }

      if (set.contains(Direction.UP)) {
         this.polygons[idx++] = new CubeModel.Polygon(
            new CubeModel.Vertex[]{vertex3, vertex4, vertex8, vertex7}, y, ad, z, ac, uTexScale, vTexScale, mirror, Direction.UP
         );
      }

      if (set.contains(Direction.WEST)) {
         this.polygons[idx++] = new CubeModel.Polygon(
            new CubeModel.Vertex[]{vertex1, vertex5, vertex8, vertex4}, w, ad, x, ae, uTexScale, vTexScale, mirror, Direction.WEST
         );
      }

      if (set.contains(Direction.NORTH)) {
         this.polygons[idx++] = new CubeModel.Polygon(
            new CubeModel.Vertex[]{vertex2, vertex1, vertex4, vertex3}, x, ad, y, ae, uTexScale, vTexScale, mirror, Direction.NORTH
         );
      }

      if (set.contains(Direction.EAST)) {
         this.polygons[idx++] = new CubeModel.Polygon(
            new CubeModel.Vertex[]{vertex6, vertex2, vertex3, vertex7}, y, ad, aa, ae, uTexScale, vTexScale, mirror, Direction.EAST
         );
      }

      if (set.contains(Direction.SOUTH)) {
         this.polygons[idx] = new CubeModel.Polygon(
            new CubeModel.Vertex[]{vertex5, vertex6, vertex7, vertex8}, aa, ad, ab, ae, uTexScale, vTexScale, mirror, Direction.SOUTH
         );
      }
   }

   public void transformVertices(Matrix4f matrix) {
      for (int i = 0; i < 8; i++) {
         CubeModel.Vertex vertex = this.vertices[i];
         vertex.pos.mulPosition(matrix, vertex.transformed);
      }
   }

   public CubeModel.Polygon[] getPolygons() {
      return this.polygons;
   }

   public record Polygon(CubeModel.Vertex[] vertices, Vector3fc normal) {
      public Polygon(CubeModel.Vertex[] vertices, float u0, float v0, float u1, float v1, float uSize, float vSize, boolean mirror, Direction direction) {
         this(vertices, (mirror ? mirrorFacing(direction) : direction).getUnitVec3f());
         float l = 0.0F / uSize;
         float m = 0.0F / vSize;
         vertices[0] = vertices[0].remap(u1 / uSize - l, v0 / vSize + m);
         vertices[1] = vertices[1].remap(u0 / uSize + l, v0 / vSize + m);
         vertices[2] = vertices[2].remap(u0 / uSize + l, v1 / vSize - m);
         vertices[3] = vertices[3].remap(u1 / uSize - l, v1 / vSize - m);
         if (mirror) {
            int n = vertices.length;

            for (int o = 0; o < n / 2; o++) {
               CubeModel.Vertex vertex = vertices[o];
               vertices[o] = vertices[n - 1 - o];
               vertices[n - 1 - o] = vertex;
            }
         }
      }

      public Polygon {
      }

      private static Direction mirrorFacing(Direction direction) {
         return direction.getAxis() == Axis.X ? direction.getOpposite() : direction;
      }
   }

   public static class Vertex {
      private static final float SCALE_FACTOR = 16.0F;
      final Vector3f pos;
      final Vector3f transformed;
      float u;
      float v;

      public Vertex(float x, float y, float z, float u, float v) {
         this.pos = new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F);
         this.transformed = new Vector3f();
         this.u = u;
         this.v = v;
      }

      public Vertex(Vector3f pos, Vector3f transformed, float u, float v) {
         this.pos = pos;
         this.transformed = transformed;
         this.u = u;
         this.v = v;
      }

      CubeModel.Vertex remap(float u, float v) {
         return new CubeModel.Vertex(this.pos, this.transformed, u, v);
      }

      public Vector3f pos() {
         return this.transformed;
      }

      public float u() {
         return this.u;
      }

      public float v() {
         return this.v;
      }
   }
}
