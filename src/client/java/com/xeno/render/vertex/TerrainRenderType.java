package com.xeno.render.vertex;

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


import java.util.EnumSet;
import java.util.function.Function;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.xeno.Initializer;
import com.xeno.interfaces.ExtendedRenderType;
import com.xeno.vulkan.VRenderSystem;

public enum TerrainRenderType {
   SOLID(0.0F),
   CUTOUT(0.5F),
   TRANSLUCENT(0.01F),
   TRIPWIRE(0.1F);

   public static final TerrainRenderType[] VALUES = values();
   public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT, TRANSLUCENT);
   public static final EnumSet<TerrainRenderType> SEMI_COMPACT_RENDER_TYPES = EnumSet.of(SOLID, CUTOUT, TRANSLUCENT);
   private static final TerrainRenderType[] remappedTypes = new TerrainRenderType[4];
   public final float alphaCutout;

   TerrainRenderType(float alphaCutout) {
      this.alphaCutout = alphaCutout;
   }

   public void setCutoutUniform() {
      VRenderSystem.alphaCutout = this.alphaCutout;
   }

   public static TerrainRenderType get(RenderType renderType) {
      return ((ExtendedRenderType)renderType).getTerrainRenderType();
   }

   public static TerrainRenderType get(ChunkSectionLayer layer) {
      return switch (layer) {
         case SOLID -> SOLID;
         case CUTOUT -> CUTOUT;
         case TRANSLUCENT -> TRANSLUCENT;
         default -> throw new MatchException(null, null);
      };
   }

   public static TerrainRenderType get(String name) {
      return switch (name) {
         case "solid" -> SOLID;
         case "cutout" -> CUTOUT;
         case "translucent" -> TRANSLUCENT;
         case "tripwire" -> TRIPWIRE;
         default -> null;
      };
   }

   public static ChunkSectionLayer getLayer(TerrainRenderType renderType) {
      return switch (renderType) {
         case SOLID -> ChunkSectionLayer.SOLID;
         case CUTOUT -> ChunkSectionLayer.CUTOUT;
         case TRANSLUCENT -> ChunkSectionLayer.TRANSLUCENT;
         case TRIPWIRE -> ChunkSectionLayer.TRANSLUCENT;
      };
   }

   public static void updateMapping() {
      if (Initializer.CONFIG.uniqueOpaqueLayer) {
         remappedTypes[SOLID.ordinal()] = CUTOUT;
         remappedTypes[CUTOUT.ordinal()] = CUTOUT;
         remappedTypes[TRANSLUCENT.ordinal()] = TRANSLUCENT;
         remappedTypes[TRIPWIRE.ordinal()] = TRANSLUCENT;
      } else {
         remappedTypes[SOLID.ordinal()] = SOLID;
         remappedTypes[CUTOUT.ordinal()] = CUTOUT;
         remappedTypes[TRANSLUCENT.ordinal()] = TRANSLUCENT;
         remappedTypes[TRIPWIRE.ordinal()] = TRANSLUCENT;
      }
   }

   public static TerrainRenderType getRemapped(TerrainRenderType renderType) {
      return remappedTypes[renderType.ordinal()];
   }

   static {
      remappedTypes[SOLID.ordinal()] = SOLID;
      remappedTypes[CUTOUT.ordinal()] = CUTOUT;
      remappedTypes[TRANSLUCENT.ordinal()] = TRANSLUCENT;
      remappedTypes[TRIPWIRE.ordinal()] = TRANSLUCENT;

      SEMI_COMPACT_RENDER_TYPES.add(CUTOUT);
      SEMI_COMPACT_RENDER_TYPES.add(TRANSLUCENT);
      COMPACT_RENDER_TYPES.add(TRANSLUCENT);
   }
}
