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
package com.xeno.mixin.render.block;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import com.xeno.render.chunk.build.frapi.helper.NormalHelper;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.model.quad.ModelQuadFlags;
import com.xeno.render.model.quad.ModelQuadView;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedQuad.class)
public abstract class BakedQuadM implements ModelQuadView {
   @Shadow
   @Final
   protected Direction direction;
   @Shadow
   @Final
   private BakedQuad.MaterialInfo materialInfo;
   private int flags;
   private int normal;
   private QuadFacing facing;

   @Shadow
   public abstract Vector3fc position(int var1);

   @Shadow
   public abstract long packedUV(int var1);

   @Inject(method = "<init>", at = @At("RETURN"))
   private void onInit(
      Vector3fc position0,
      Vector3fc position1,
      Vector3fc position2,
      Vector3fc position3,
      long packedUV0,
      long packedUV1,
      long packedUV2,
      long packedUV3,
      Direction direction,
      BakedQuad.MaterialInfo materialInfo,
      CallbackInfo ci
   ) {
      this.flags = ModelQuadFlags.getQuadFlags(this, direction);
      int packedNormal = NormalHelper.computePackedNormal(this);
      this.normal = packedNormal;
      this.facing = QuadFacing.fromNormal(packedNormal);
   }

   @Override
   public int getFlags() {
      return this.flags;
   }

   @Override
   public float getX(int idx) {
      return this.position(idx).x();
   }

   @Override
   public float getY(int idx) {
      return this.position(idx).y();
   }

   @Override
   public float getZ(int idx) {
      return this.position(idx).z();
   }

   @Override
   public int getColor(int idx) {
      return -1;
   }

   @Override
   public float getU(int idx) {
      return UVPair.unpackU(this.packedUV(idx));
   }

   @Override
   public float getV(int idx) {
      return UVPair.unpackV(this.packedUV(idx));
   }

   @Override
   public int getColorIndex() {
      return this.materialInfo.tintIndex();
   }

   @Override
   public Direction lightFace() {
      return this.direction;
   }

   @Override
   public Direction getFacingDirection() {
      return this.direction;
   }

   @Override
   public QuadFacing getQuadFacing() {
      return this.facing;
   }

   @Override
   public int getNormal() {
      return this.normal;
   }

   @Override
   public boolean isTinted() {
      return this.materialInfo.isTinted();
   }

   private static int vertexOffset(int vertexIndex) {
      return vertexIndex * 8;
   }
}
