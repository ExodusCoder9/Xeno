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


import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.WorldRenderer;
import com.xeno.render.chunk.build.UploadBuffer;
import com.xeno.render.chunk.build.thread.BuilderResources;
import com.xeno.render.chunk.build.thread.ThreadBuilderPack;
import com.xeno.render.vertex.QuadSorter;
import com.xeno.render.vertex.TerrainBuilder;
import com.xeno.render.vertex.TerrainRenderType;
import org.joml.Vector3d;

public class SortTransparencyTask extends ChunkTask {
   public SortTransparencyTask(RenderSection renderSection, Vector3d cameraPos) {
      super(renderSection, cameraPos);
   }

   @Override
   public String name() {
      return "rend_chk_sort";
   }

   @Override
   public ChunkTask.Result runTask(BuilderResources context) {
      ThreadBuilderPack builderPack = context.builderPack;
      if (this.cancelled.get()) {
         return ChunkTask.Result.CANCELLED;
      } else {
         Vector3d cameraPos = WorldRenderer.getCameraPos();
         float x = (float)cameraPos.x;
         float y = (float)cameraPos.y;
         float z = (float)cameraPos.z;
         CompiledSection compiledSection = this.section.getCompiledSection();
         QuadSorter.SortState transparencyState = compiledSection.transparencyState;
         if (transparencyState == null) {
            return ChunkTask.Result.CANCELLED;
         } else {
            TerrainBuilder bufferBuilder = builderPack.builder(TerrainRenderType.TRANSLUCENT);
            bufferBuilder.begin();
            bufferBuilder.restoreSortState(transparencyState);
            bufferBuilder.setupQuadSorting(x - this.section.xOffset(), y - this.section.yOffset(), z - this.section.zOffset());
            TerrainBuilder.DrawState drawState = bufferBuilder.endDrawing();
            CompileResult compileResult = new CompileResult(this.section, false);
            UploadBuffer uploadBuffer = new UploadBuffer(bufferBuilder, drawState);
            compileResult.renderedLayers.put(TerrainRenderType.TRANSLUCENT, uploadBuffer);
            bufferBuilder.reset();
            if (this.cancelled.get()) {
               compileResult.renderedLayers.values().forEach(UploadBuffer::release);
               return ChunkTask.Result.CANCELLED;
            } else {
               taskDispatcher.scheduleSectionUpdate(compileResult);
               return ChunkTask.Result.SUCCESSFUL;
            }
         }
      }
   }
}
