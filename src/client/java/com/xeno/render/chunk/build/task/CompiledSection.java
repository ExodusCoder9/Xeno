package com.xeno.render.chunk.build.task;

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


import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.xeno.render.vertex.QuadSorter;
import org.jetbrains.annotations.Nullable;

public class CompiledSection {
   public static final CompiledSection UNCOMPILED = new CompiledSection();
   boolean isCompletelyEmpty = false;
   final List<BlockEntity> blockEntities = Lists.newArrayList();
   @Nullable
   QuadSorter.SortState transparencyState;

   public CompiledSection() {
   }

   public boolean hasTransparencyState() {
      return this.transparencyState != null;
   }

   public List<BlockEntity> getBlockEntities() {
      return this.blockEntities;
   }
}
