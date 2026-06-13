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


import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.xeno.interfaces.VisibilitySetExtended;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.build.UploadBuffer;
import com.xeno.render.vertex.QuadSorter;
import com.xeno.render.vertex.TerrainRenderType;

public class CompileResult {
   public final RenderSection renderSection;
   public final boolean fullUpdate;
   final List<BlockEntity> globalBlockEntities = new ArrayList<>();
   final List<BlockEntity> blockEntities = new ArrayList<>();
   public final EnumMap<TerrainRenderType, UploadBuffer> renderedLayers = new EnumMap<>(TerrainRenderType.class);
   VisibilitySet visibilitySet;
   QuadSorter.SortState transparencyState;
   CompiledSection compiledSection;

   CompileResult(RenderSection renderSection, boolean fullUpdate) {
      this.renderSection = renderSection;
      this.fullUpdate = fullUpdate;
   }

   public void updateSection() {
      this.renderSection.updateGlobalBlockEntities(this.globalBlockEntities);
      this.renderSection.setCompiledSection(this.compiledSection);
      this.renderSection.setVisibility(((VisibilitySetExtended)this.visibilitySet).getVisibility());
      this.renderSection.setCompletelyEmpty(this.compiledSection.isCompletelyEmpty);
      this.renderSection.setContainsBlockEntities(!this.blockEntities.isEmpty());
   }
}
