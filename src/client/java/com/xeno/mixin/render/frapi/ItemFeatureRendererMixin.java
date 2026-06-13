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
package com.xeno.mixin.render.frapi;

import net.fabricmc.fabric.api.client.renderer.v1.render.FabricSubmitNodeCollection.ExtendedItemSubmit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import com.xeno.render.chunk.build.frapi.render.AltItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
abstract class ItemFeatureRendererMixin {
   @Unique
   private final AltItemRenderer altItemRenderer = new AltItemRenderer();

   @Inject(method = "renderSolid", at = @At("RETURN"))
   private void onReturnRenderSolid(
      SubmitNodeCollection nodeCollection, MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource, CallbackInfo ci
   ) {
      this.altItemRenderer.prepare(bufferSource, outlineBufferSource, false);

      for (ExtendedItemSubmit submit : nodeCollection.getExtendedItemSubmits()) {
         this.altItemRenderer.renderItem(submit);
      }

      this.altItemRenderer.clear();
   }

   @Inject(method = "renderTranslucent", at = @At("RETURN"))
   private void onReturnRenderTranslucent(
      SubmitNodeCollection nodeCollection, MultiBufferSource.BufferSource bufferSource, OutlineBufferSource outlineBufferSource, CallbackInfo ci
   ) {
      this.altItemRenderer.prepare(bufferSource, outlineBufferSource, true);

      for (ExtendedItemSubmit submit : nodeCollection.getExtendedItemSubmits()) {
         this.altItemRenderer.renderItem(submit);
      }

      this.altItemRenderer.clear();
   }
}
