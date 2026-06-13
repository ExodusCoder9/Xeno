package com.xeno.render.chunk.build.frapi.mesh;

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


import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;

enum ItemRenderType {
   CUTOUT(Sheets.cutoutItemSheet()),
   TRANSLUCENT(Sheets.translucentItemSheet()),
   CUTOUT_BLOCK(Sheets.cutoutBlockItemSheet()),
   TRANSLUCENT_BLOCK(Sheets.translucentBlockItemSheet());

   static final RenderType[] RENDER_TYPES = Arrays.stream(values()).map(t -> t.renderType).toArray(RenderType[]::new);
   static final Map<RenderType, ItemRenderType> RENDER_TYPE_2_ENUM;
   static final ItemRenderType DEFAULT = CUTOUT_BLOCK;
   final RenderType renderType;

   ItemRenderType(RenderType renderType) {
      this.renderType = renderType;
   }

   static {
      RENDER_TYPE_2_ENUM = Map.of(
         CUTOUT.renderType, CUTOUT, TRANSLUCENT.renderType, TRANSLUCENT, CUTOUT_BLOCK.renderType, CUTOUT_BLOCK, TRANSLUCENT_BLOCK.renderType, TRANSLUCENT_BLOCK
      );
   }
}
