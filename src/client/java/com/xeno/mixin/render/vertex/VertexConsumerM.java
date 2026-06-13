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
package com.xeno.mixin.render.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import com.xeno.mixin.matrix.PoseAccessor;
import com.xeno.render.util.MathUtil;
import com.xeno.render.vertex.format.I32_SNorm;
import com.xeno.vulkan.util.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(VertexConsumer.class)
public interface VertexConsumerM {
   @Shadow
   void addVertex(float var1, float var2, float var3, int var4, float var5, float var6, int var7, int var8, float var9, float var10, float var11);

   @Overwrite
   default void putBlockBakedQuad(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
      Vector3fc normal = quad.direction().getUnitVec3f();
      int lightEmission = quad.materialInfo().lightEmission();

      for (int vertex = 0; vertex < 4; vertex++) {
         Vector3fc pos = quad.position(vertex);
         long packedUv = quad.packedUV(vertex);
         int vertexColor = instance.getColor(vertex);
         vertexColor = ColorUtil.RGBA.fromArgb32(vertexColor);
         int light = instance.getLightCoordsWithEmission(vertex, lightEmission);
         float u = UVPair.unpackU(packedUv);
         float v = UVPair.unpackV(packedUv);
         this.addVertex(pos.x() + x, pos.y() + y, pos.z() + z, vertexColor, u, v, instance.overlayCoords(), light, normal.x(), normal.y(), normal.z());
      }
   }

   @Overwrite
   default void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
      Vector3fc normalVec = quad.direction().getUnitVec3f();
      Matrix4f matrix = pose.pose();
      boolean trustedNormals = ((PoseAccessor)(Object)pose).trustedNormals();
      int packedNormal = MathUtil.packTransformedNorm(pose.normal(), trustedNormals, normalVec.x(), normalVec.y(), normalVec.z());
      int lightEmission = quad.materialInfo().lightEmission();

      for (int vertex = 0; vertex < 4; vertex++) {
         Vector3fc position = quad.position(vertex);
         long packedUv = quad.packedUV(vertex);
         int vertexColor = instance.getColor(vertex);
         vertexColor = ColorUtil.RGBA.fromArgb32(vertexColor);
         int light = instance.getLightCoordsWithEmission(vertex, lightEmission);
         float x = position.x();
         float y = position.y();
         float z = position.z();
         float tx = MathUtil.transformX(matrix, x, y, z);
         float ty = MathUtil.transformY(matrix, x, y, z);
         float tz = MathUtil.transformZ(matrix, x, y, z);
         float u = UVPair.unpackU(packedUv);
         float v = UVPair.unpackV(packedUv);
         this.addVertex(
            tx,
            ty,
            tz,
            vertexColor,
            u,
            v,
            instance.overlayCoords(),
            light,
            I32_SNorm.unpackX(packedNormal),
            I32_SNorm.unpackY(packedNormal),
            I32_SNorm.unpackZ(packedNormal)
         );
      }
   }
}
