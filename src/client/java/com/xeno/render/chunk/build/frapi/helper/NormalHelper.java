package com.xeno.render.chunk.build.frapi.helper;

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


import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import com.xeno.render.model.quad.ModelQuadView;
import com.xeno.render.vertex.format.I32_SNorm;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public abstract class NormalHelper {
   private static final float PACK = 127.0F;
   private static final float UNPACK = 0.007874016F;

   private NormalHelper() {
   }

   public static int packNormal(float x, float y, float z, float w) {
      x = Mth.clamp(x, -1.0F, 1.0F);
      y = Mth.clamp(y, -1.0F, 1.0F);
      z = Mth.clamp(z, -1.0F, 1.0F);
      w = Mth.clamp(w, -1.0F, 1.0F);
      return (int)(x * 127.0F) & 0xFF | ((int)(y * 127.0F) & 0xFF) << 8 | ((int)(z * 127.0F) & 0xFF) << 16 | ((int)(w * 127.0F) & 0xFF) << 24;
   }

   public static int packNormal(Vector3f normal, float w) {
      return packNormal(normal.x(), normal.y(), normal.z(), w);
   }

   public static int packNormal(float x, float y, float z) {
      x = Mth.clamp(x, -1.0F, 1.0F);
      y = Mth.clamp(y, -1.0F, 1.0F);
      z = Mth.clamp(z, -1.0F, 1.0F);
      return (int)(x * 127.0F) & 0xFF | ((int)(y * 127.0F) & 0xFF) << 8 | ((int)(z * 127.0F) & 0xFF) << 16;
   }

   public static int packNormal(Vector3f normal) {
      return packNormal(normal.x(), normal.y(), normal.z());
   }

   public static float unpackNormalX(int packedNormal) {
      return (byte)(packedNormal & 0xFF) * 0.007874016F;
   }

   public static float unpackNormalY(int packedNormal) {
      return (byte)(packedNormal >>> 8 & 0xFF) * 0.007874016F;
   }

   public static float unpackNormalZ(int packedNormal) {
      return (byte)(packedNormal >>> 16 & 0xFF) * 0.007874016F;
   }

   public static float unpackNormalW(int packedNormal) {
      return (byte)(packedNormal >>> 24 & 0xFF) * 0.007874016F;
   }

   public static void unpackNormal(int packedNormal, Vector3f target) {
      target.set(unpackNormalX(packedNormal), unpackNormalY(packedNormal), unpackNormalZ(packedNormal));
   }

   public static void computeFaceNormal(@NotNull Vector3f saveTo, QuadView q) {
      Direction nominalFace = q.nominalFace();
      if (nominalFace != null && GeometryHelper.isQuadParallelToFace(nominalFace, q)) {
         Vec3i vec = nominalFace.getUnitVec3i();
         saveTo.set(vec.getX(), vec.getY(), vec.getZ());
      } else {
         float x0 = q.x(0);
         float y0 = q.y(0);
         float z0 = q.z(0);
         float x1 = q.x(1);
         float y1 = q.y(1);
         float z1 = q.z(1);
         float x2 = q.x(2);
         float y2 = q.y(2);
         float z2 = q.z(2);
         float x3 = q.x(3);
         float y3 = q.y(3);
         float z3 = q.z(3);
         float dx0 = x2 - x0;
         float dy0 = y2 - y0;
         float dz0 = z2 - z0;
         float dx1 = x3 - x1;
         float dy1 = y3 - y1;
         float dz1 = z3 - z1;
         float normX = dy0 * dz1 - dz0 * dy1;
         float normY = dz0 * dx1 - dx0 * dz1;
         float normZ = dx0 * dy1 - dy0 * dx1;
         float l = (float)Math.sqrt(normX * normX + normY * normY + normZ * normZ);
         if (l != 0.0F) {
            normX /= l;
            normY /= l;
            normZ /= l;
         }

         saveTo.set(normX, normY, normZ);
      }
   }

   public static int computePackedNormal(ModelQuadView q) {
      float x0 = q.getX(0);
      float y0 = q.getY(0);
      float z0 = q.getZ(0);
      float x1 = q.getX(1);
      float y1 = q.getY(1);
      float z1 = q.getZ(1);
      float x2 = q.getX(2);
      float y2 = q.getY(2);
      float z2 = q.getZ(2);
      float x3 = q.getX(3);
      float y3 = q.getY(3);
      float z3 = q.getZ(3);
      float dx0 = x2 - x0;
      float dy0 = y2 - y0;
      float dz0 = z2 - z0;
      float dx1 = x3 - x1;
      float dy1 = y3 - y1;
      float dz1 = z3 - z1;
      float normX = dy0 * dz1 - dz0 * dy1;
      float normY = dz0 * dx1 - dx0 * dz1;
      float normZ = dx0 * dy1 - dy0 * dx1;
      float l = (float)Math.sqrt(normX * normX + normY * normY + normZ * normZ);
      if (l != 0.0F) {
         normX /= l;
         normY /= l;
         normZ /= l;
      }

      return I32_SNorm.packNormal(normX, normY, normZ);
   }

   public static int packedNormalFromDirection(Direction direction) {
      Vec3i normal = direction.getUnitVec3i();
      return I32_SNorm.packNormal(normal.getX(), normal.getY(), normal.getZ());
   }

   public static void unpackNormalTo(int packedNormal, Vector3f normal) {
      normal.set(I32_SNorm.unpackX(packedNormal), I32_SNorm.unpackY(packedNormal), I32_SNorm.unpackZ(packedNormal));
   }
}
