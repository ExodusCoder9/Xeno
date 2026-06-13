package com.xeno.render.chunk.build.color;

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


import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.List;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.Block;

public class BlockColorRegistry {
   private final Reference2ReferenceOpenHashMap<Block, List<BlockTintSource>> map = new Reference2ReferenceOpenHashMap();

   public BlockColorRegistry() {
   }

   public void register(List<BlockTintSource> layers, Block... blocks) {
      for (Block block : blocks) {
         this.map.put(block, layers);
      }
   }

   public List<BlockTintSource> getTintSources(Block block) {
      return (List<BlockTintSource>)this.map.getOrDefault(block, List.of());
   }
}
