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
package com.xeno.render.profiling;

import java.util.List;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import com.xeno.render.chunk.ChunkAreaManager;
import com.xeno.render.chunk.WorldRenderer;
import org.jetbrains.annotations.Nullable;

public class DebugEntryMemoryStats implements DebugScreenEntry {
   private static final Identifier GROUP = Identifier.withDefaultNamespace("vk_memory");

   @Override
   public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
      ChunkAreaManager chunkAreaManager = WorldRenderer.getInstance().getChunkAreaManager();
      if (chunkAreaManager != null) {
         debugScreenDisplayer.addToGroup(GROUP, List.of(chunkAreaManager.getStats()));
      }
   }
}
