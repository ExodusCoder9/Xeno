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
package com.xeno.mixin.render.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import com.xeno.interfaces.ExtendedVertexBuilder;
import com.xeno.vulkan.util.ColorUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(QuadParticleRenderState.class)
public class QuadParticleRenderStateM {
   @Unique
   private final Quaternionf quaternionf = new Quaternionf();
   @Unique
   private final Vector3f vector3f = new Vector3f();

   @Overwrite
   public void renderRotatedQuad(
      VertexConsumer vertexConsumer,
      float x,
      float y,
      float z,
      float xr,
      float yr,
      float zr,
      float wr,
      float m,
      float u0,
      float u1,
      float v0,
      float v1,
      int color,
      int light
   ) {
      this.quaternionf.set(xr, yr, zr, wr);
      ExtendedVertexBuilder vertexBuilder = (ExtendedVertexBuilder)vertexConsumer;
      color = ColorUtil.BGRAtoRGBA(color);
      this.renderVertex(vertexBuilder, this.quaternionf, x, y, z, 1.0F, -1.0F, m, u1, v1, color, light);
      this.renderVertex(vertexBuilder, this.quaternionf, x, y, z, 1.0F, 1.0F, m, u1, v0, color, light);
      this.renderVertex(vertexBuilder, this.quaternionf, x, y, z, -1.0F, 1.0F, m, u0, v0, color, light);
      this.renderVertex(vertexBuilder, this.quaternionf, x, y, z, -1.0F, -1.0F, m, u0, v1, color, light);
   }

   private void renderVertex(
      ExtendedVertexBuilder vertexConsumer,
      Quaternionf quaternionf,
      float x,
      float y,
      float z,
      float i,
      float j,
      float k,
      float u,
      float v,
      int color,
      int light
   ) {
      this.vector3f.set(i, j, 0.0F).rotate(quaternionf).mul(k).add(x, y, z);
      vertexConsumer.vertex(this.vector3f.x(), this.vector3f.y(), this.vector3f.z(), u, v, color, light);
   }
}
