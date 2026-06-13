package com.xeno.render.chunk.build.renderer;

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


import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.xeno.render.chunk.build.frapi.mesh.EncodingFormat;
import com.xeno.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public abstract class AbstractRenderContext {
   private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
      {
         this.data = new int[EncodingFormat.TOTAL_STRIDE];
         this.clear();
      }

      @Override
      protected void emitDirectly() {
         AbstractRenderContext.this.bufferQuad(this);
      }
   };
   private final Vector4f posVec = new Vector4f();
   private final Vector3f normalVec = new Vector3f();
   protected Pose matrices;
   protected int overlay;

   public AbstractRenderContext() {
   }

   protected abstract void bufferQuad(MutableQuadViewImpl var1);

   protected void bufferQuad(MutableQuadViewImpl quad, VertexConsumer vertexConsumer) {
      Vector4f posVec = this.posVec;
      Vector3f normalVec = this.normalVec;
      Pose matrices = this.matrices;
      Matrix4f posMatrix = matrices.pose();
      boolean useNormals = quad.hasVertexNormals();
      if (useNormals) {
         quad.populateMissingNormals();
      } else {
         matrices.transformNormal(quad.faceNormal(), normalVec);
      }

      for (int i = 0; i < 4; i++) {
         posVec.set(quad.x(i), quad.y(i), quad.z(i), 1.0F);
         posVec.mul(posMatrix);
         if (useNormals) {
            quad.copyNormal(i, normalVec);
            matrices.transformNormal(normalVec, normalVec);
         }

         vertexConsumer.addVertex(
            posVec.x(),
            posVec.y(),
            posVec.z(),
            quad.color(i),
            quad.u(i),
            quad.v(i),
            this.overlay,
            quad.lightmap(i),
            normalVec.x(),
            normalVec.y(),
            normalVec.z()
         );
      }
   }
}
