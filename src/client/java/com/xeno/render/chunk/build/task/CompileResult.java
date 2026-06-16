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

public final class CompileResult {
   public final RenderSection renderSection;
   public final boolean fullUpdate;
   private List<BlockEntity> globalBlockEntities = java.util.Collections.emptyList();
   private List<BlockEntity> blockEntities = java.util.Collections.emptyList();
   public final UploadBuffer[] renderedLayers = new UploadBuffer[TerrainRenderType.VALUES.length];
   VisibilitySet visibilitySet;
   QuadSorter.SortState transparencyState;

   // ----- Accessors for block entity collections -----
   public java.util.List<BlockEntity> getBlockEntities() {
      return this.blockEntities;
   }

   public java.util.List<BlockEntity> getGlobalBlockEntities() {
      return this.globalBlockEntities;
   }

   private void ensureBlockEntitiesMutable() {
      if (this.blockEntities.isEmpty()) {
         this.blockEntities = new java.util.ArrayList<>();
      }
   }

   private void ensureGlobalBlockEntitiesMutable() {
      if (this.globalBlockEntities.isEmpty()) {
         this.globalBlockEntities = new java.util.ArrayList<>();
      }
   }

   public void addBlockEntity(BlockEntity be) {
      ensureBlockEntitiesMutable();
      this.blockEntities.add(be);
   }

   public void addGlobalBlockEntity(BlockEntity be) {
      ensureGlobalBlockEntitiesMutable();
      this.globalBlockEntities.add(be);
   }
   CompiledSection compiledSection;

   CompileResult(RenderSection renderSection, boolean fullUpdate) {
      this.renderSection = renderSection;
      this.fullUpdate = fullUpdate;
   }

   public boolean hasNoRenderedLayers() {
      for (UploadBuffer buffer : this.renderedLayers) {
         if (buffer != null) {
            return false;
         }
      }
      return true;
   }

   public void release() {
      for (int i = 0; i < this.renderedLayers.length; i++) {
         UploadBuffer buffer = this.renderedLayers[i];
         if (buffer != null) {
            buffer.release();
            this.renderedLayers[i] = null;
         }
      }
   }

   public void updateSection() {
      this.renderSection.updateGlobalBlockEntities(this.globalBlockEntities);
      this.renderSection.setCompiledSection(this.compiledSection);
      this.renderSection.setVisibility(((VisibilitySetExtended)this.visibilitySet).getVisibility());
      this.renderSection.setCompletelyEmpty(this.compiledSection.isCompletelyEmpty);
      this.renderSection.setContainsBlockEntities(!this.blockEntities.isEmpty());
   }
}
