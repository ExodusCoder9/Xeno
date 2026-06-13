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
package com.xeno.mixin.render.entity;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import com.xeno.Initializer;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public class EntityRendererM<T extends Entity> {
   @Redirect(
      method = "shouldRender",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z")
   )
   private boolean isVisible(Frustum frustum, AABB aABB) {
      if (Initializer.CONFIG.entityCulling) {
         WorldRenderer worldRenderer = WorldRenderer.getInstance();
         double cx = (aABB.minX + aABB.maxX) * 0.5;
         double cy = (aABB.minY + aABB.maxY) * 0.5;
         double cz = (aABB.minZ + aABB.maxZ) * 0.5;
         RenderSection section = worldRenderer.getSectionGrid().getSectionAtBlockPos((int)cx, (int)cy, (int)cz);
         return section == null ? frustum.isVisible(aABB) : worldRenderer.getLastFrame() == section.getLastFrame();
      } else {
         return frustum.isVisible(aABB);
      }
   }
}
