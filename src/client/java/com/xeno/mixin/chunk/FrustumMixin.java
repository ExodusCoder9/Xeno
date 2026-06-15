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
package com.xeno.mixin.chunk;

import net.minecraft.client.renderer.culling.Frustum;
import com.xeno.interfaces.FrustumMixed;
import com.xeno.render.chunk.frustum.VFrustum;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Frustum.class)
public class FrustumMixin implements FrustumMixed {
   @Shadow
   private double camX;
   @Shadow
   private double camY;
   @Shadow
   private double camZ;
   @Shadow
   @Final
   private Matrix4f matrix;
   @Shadow
   private Vector4f viewVector;
    @Unique
    private final VFrustum vFrustum = new VFrustum();
    @Unique
    private final Vector4f tempVector = new Vector4f();

    @Inject(method = "calculateFrustum", at = @At("HEAD"))
    private void calculateFrustum(Matrix4fc modelView, Matrix4f projection, CallbackInfo ci) {
       this.vFrustum.calculateFrustum((Matrix4f)modelView, projection);
       this.viewVector = this.matrix.transformTranspose(this.tempVector.set(0.0F, 0.0F, 1.0F, 0.0F));
    }

   @Inject(method = "prepare", at = @At("RETURN"))
   public void prepare(double d, double e, double f, CallbackInfo ci) {
      this.vFrustum.setCamOffset(this.camX, this.camY, this.camZ);
   }

   @Override
   public VFrustum customFrustum() {
      return this.vFrustum;
   }
}
