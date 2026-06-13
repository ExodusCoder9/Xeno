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

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.xeno.interfaces.ExtendedVertexBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteCoordinateExpander.class)
public class SpriteCoordinateExpanderM implements ExtendedVertexBuilder {
   @Shadow
   @Final
   private TextureAtlasSprite sprite;
   @Unique
   private ExtendedVertexBuilder extDelegate;
   @Unique
   private boolean canUseFastVertex = false;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void getExtBuilder(VertexConsumer vertexConsumer, TextureAtlasSprite textureAtlasSprite, CallbackInfo ci) {
      if (vertexConsumer instanceof ExtendedVertexBuilder) {
         this.extDelegate = (ExtendedVertexBuilder)vertexConsumer;
         this.canUseFastVertex = true;
      }
   }

   @Override
   public boolean canUseFastVertex() {
      return this.canUseFastVertex;
   }

   @Override
   public void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
      this.extDelegate.vertex(x, y, z, packedColor, this.sprite.getU(u), this.sprite.getV(v), overlay, light, packedNormal);
   }
}
