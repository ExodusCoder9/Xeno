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
package com.xeno.mixin.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin {
   @Shadow
   @Final
   private DynamicAtlasAllocator<Object> allocator;
   @Shadow
   @Final
   private int slotTextureSize;
   @Shadow
   @Final
   private GpuTextureView textureView;
   @Shadow
   @Final
   private int textureSize;

   @Shadow
   protected abstract void drawToSlot(int var1, int var2, boolean var3, ItemStackRenderState var4);

   @Inject(method = "getOrUpdate", at = @At("HEAD"), cancellable = true)
   public void getOrUpdateInj(TrackingItemStackRenderState item, CallbackInfoReturnable<GuiItemAtlas.SlotView> cir) {
      DynamicAtlasAllocator.Slot slot = this.allocator.getOrAllocate(item.getModelIdentity(), item.isAnimated());
      if (slot == null) {
         cir.setReturnValue(null);
      } else {
         switch (slot.state()) {
            case EMPTY:
               this.drawToSlot(slot.x(), slot.y(), false, item);
               break;
            case STALE:
               this.drawToSlot(slot.x(), slot.y(), true, item);
            case READY:
         }

         float slotUvSize = (float)this.slotTextureSize / this.textureSize;
         float u0 = slot.x() * slotUvSize;
         float v0 = 1.0F - slot.y() * slotUvSize;
         v0 = 1.0F - v0;
         cir.setReturnValue(new GuiItemAtlas.SlotView(this.textureView, u0, v0, u0 + slotUvSize, v0 + slotUvSize));
      }
   }
}
