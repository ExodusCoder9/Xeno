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
package com.xeno.mixin.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import com.xeno.interfaces.ExtendedVertexBuilder;
import com.xeno.render.vertex.format.I32_SNorm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class VertexMultiConsumersM {
   @Mixin(targets = "com/mojang/blaze3d/vertex/VertexMultiConsumer$Double")
   public static class DoubleM implements ExtendedVertexBuilder {
      @Shadow
      @Final
      private VertexConsumer first;
      @Shadow
      @Final
      private VertexConsumer second;
      @Unique
      private ExtendedVertexBuilder firstExt;
      @Unique
      private ExtendedVertexBuilder secondExt;
      @Unique
      private boolean canUseFastVertex = false;

      @Override
      public boolean canUseFastVertex() {
         return this.canUseFastVertex;
      }

      @Inject(method = "<init>", at = @At("RETURN"))
      private void checkDelegates(VertexConsumer vertexConsumer, VertexConsumer vertexConsumer2, CallbackInfo ci) {
         this.canUseFastVertex = ExtendedVertexBuilder.of(this.first) != null && ExtendedVertexBuilder.of(this.second) != null;
         if (this.canUseFastVertex) {
            this.firstExt = ExtendedVertexBuilder.of(this.first);
            this.secondExt = ExtendedVertexBuilder.of(this.second);
         }
      }

      @Override
      public void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
         this.firstExt.vertex(x, y, z, packedColor, u, v, overlay, light, packedNormal);
         this.secondExt.vertex(x, y, z, packedColor, u, v, overlay, light, packedNormal);
      }
   }

   @Mixin(targets = "com/mojang/blaze3d/vertex/VertexMultiConsumer$Multiple")
   public static class MultipleM implements ExtendedVertexBuilder {
      @Shadow
      @Final
      private VertexConsumer[] delegates;
      @Unique
      private boolean canUseFastVertex = false;

      @Override
      public boolean canUseFastVertex() {
         return this.canUseFastVertex;
      }

      @Inject(method = "<init>", at = @At("RETURN"))
      private void checkDelegates(VertexConsumer[] vertexConsumers, CallbackInfo ci) {
         for (VertexConsumer delegate : this.delegates) {
            if (ExtendedVertexBuilder.of(delegate) == null) {
               this.canUseFastVertex = false;
               return;
            }
         }

         this.canUseFastVertex = true;
      }

      @Override
      public void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
         for (VertexConsumer vertexConsumer : this.delegates) {
            ExtendedVertexBuilder extendedVertexBuilder = (ExtendedVertexBuilder)vertexConsumer;
            extendedVertexBuilder.vertex(x, y, z, packedColor, u, v, overlay, light, packedNormal);
         }
      }
   }

   @Mixin(SheetedDecalTextureGenerator.class)
   public abstract static class SheetDecalM implements ExtendedVertexBuilder {
      @Shadow
      @Final
      private VertexConsumer delegate;
      @Shadow
      @Final
      private Matrix3f normalInversePose;
      @Shadow
      @Final
      private Matrix4f cameraInversePose;
      @Shadow
      @Final
      private float textureScale;
      @Unique
      private boolean canUseFastVertex = false;
      private Vector3f normal = new Vector3f();
      private Vector4f position = new Vector4f();

      @Override
      public boolean canUseFastVertex() {
         return this.canUseFastVertex;
      }

      @Inject(method = "<init>", at = @At("RETURN"))
      private void checkDelegates(VertexConsumer vertexConsumer, PoseStack.Pose pose, float f, CallbackInfo ci) {
         this.canUseFastVertex = ExtendedVertexBuilder.of(this.delegate) != null;
      }

      @Override
      public void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
         float nx = I32_SNorm.unpackX(packedNormal);
         float ny = I32_SNorm.unpackY(packedNormal);
         float nz = I32_SNorm.unpackZ(packedNormal);
         this.normal.set(nx, ny, nz);
         this.position.set(x, y, z, 1.0F);
         this.normalInversePose.transform(this.normal);
         Direction direction = Direction.getApproximateNearest(this.normal.x(), this.normal.y(), this.normal.z());
         this.cameraInversePose.transform(this.position);
         this.position.rotateY((float) Math.PI);
         this.position.rotateX((float) (-Math.PI / 2));
         this.position.rotate(direction.getRotation());
         float f = -this.position.x() * this.textureScale;
         float g = -this.position.y() * this.textureScale;
         int color = -1;
         this.delegate.addVertex(x, y, z, -1, f, g, overlay, light, nx, ny, nz);
      }
   }
}
