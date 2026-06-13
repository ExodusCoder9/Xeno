package com.xeno.render.chunk;

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


import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.Set;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.xeno.render.chunk.buffer.AreaBuffer;
import com.xeno.render.chunk.buffer.DrawBuffers;
import com.xeno.render.chunk.buffer.DrawParametersBuffer;
import com.xeno.render.chunk.build.RenderRegion;
import com.xeno.render.chunk.build.RenderRegionBuilder;
import com.xeno.render.chunk.build.task.BuildTask;
import com.xeno.render.chunk.build.task.ChunkTask;
import com.xeno.render.chunk.build.task.CompiledSection;
import com.xeno.render.chunk.build.task.SortTransparencyTask;
import com.xeno.render.chunk.build.task.TaskDispatcher;
import com.xeno.render.chunk.cull.QuadFacing;
import com.xeno.render.chunk.util.Util;
import com.xeno.render.vertex.TerrainRenderType;
import org.joml.Vector3d;

public class RenderSection {
   private ChunkArea chunkArea;
   public byte frustumIndex;
   public short lastFrame = -1;
   private short lastFrame2 = -1;
   public short inAreaIndex;
   public byte adjDirs;
   public RenderSection adjDown;
   public RenderSection adjUp;
   public RenderSection adjNorth;
   public RenderSection adjSouth;
   public RenderSection adjWest;
   public RenderSection adjEast;
   private final RenderSection.CompileStatus compileStatus = new RenderSection.CompileStatus();
   private boolean dirty = true;
   private boolean playerChanged;
   private boolean completelyEmpty = true;
   private boolean containsBlockEntities = false;
   public long visibility;
   public int xOffset;
   public int yOffset;
   public int zOffset;
   public byte mainDir;
   public byte directions;
   public byte sourceDirs;
   public byte steps;
   public byte directionChanges;

   public RenderSection(int index, int x, int y, int z) {
      this.xOffset = x;
      this.yOffset = y;
      this.zOffset = z;
   }

   public void setOrigin(int x, int y, int z) {
      this.reset();
      this.xOffset = x;
      this.yOffset = y;
      this.zOffset = z;
   }

   public void addDir(int direction) {
      this.sourceDirs |= (byte)(1 << direction);
   }

   public void setDirections(byte dirs, int direction) {
      this.directions = (byte)(this.directions | dirs | 1 << direction);
   }

   void setDirectionChanges(byte i) {
      this.directionChanges = i;
   }

   public boolean hasDirection(byte dir) {
      return (this.directions & 1 << dir) > 0;
   }

   public boolean hasMainDirection() {
      return this.sourceDirs != 0;
   }

   public void setAdjacent(RenderSection adjacent, int direction) {
      switch (direction) {
         case 0:
            this.adjDown = adjacent;
            adjacent.adjUp = this;
            this.addAdjDir(adjacent, direction);
            break;
         case 1:
            this.adjUp = adjacent;
            adjacent.adjDown = this;
            this.addAdjDir(adjacent, direction);
            break;
         case 2:
            this.adjNorth = adjacent;
            adjacent.adjSouth = this;
            this.addAdjDir(adjacent, direction);
            break;
         case 3:
            this.adjSouth = adjacent;
            adjacent.adjNorth = this;
            this.addAdjDir(adjacent, direction);
            break;
         case 4:
            this.adjWest = adjacent;
            adjacent.adjEast = this;
            this.addAdjDir(adjacent, direction);
            break;
         case 5:
            this.adjEast = adjacent;
            adjacent.adjWest = this;
            this.addAdjDir(adjacent, direction);
      }
   }

   public void resetAdjacent(int direction) {
      switch (direction) {
         case 0:
            RenderSection adjacentxxxxx = this.adjDown;
            if (adjacentxxxxx != null) {
               adjacentxxxxx.adjUp = null;
               this.removeAdjDir(adjacentxxxxx, direction);
            }
            break;
         case 1:
            RenderSection adjacentxxxx = this.adjUp;
            if (adjacentxxxx != null) {
               adjacentxxxx.adjDown = null;
               this.removeAdjDir(adjacentxxxx, direction);
            }
            break;
         case 2:
            RenderSection adjacentxxx = this.adjNorth;
            if (adjacentxxx != null) {
               adjacentxxx.adjSouth = null;
               this.removeAdjDir(adjacentxxx, direction);
            }
            break;
         case 3:
            RenderSection adjacentxx = this.adjSouth;
            if (adjacentxx != null) {
               adjacentxx.adjNorth = null;
               this.removeAdjDir(adjacentxx, direction);
            }
            break;
         case 4:
            RenderSection adjacentx = this.adjWest;
            if (adjacentx != null) {
               adjacentx.adjEast = null;
               this.removeAdjDir(adjacentx, direction);
            }
            break;
         case 5:
            RenderSection adjacent = this.adjEast;
            if (adjacent != null) {
               adjacent.adjWest = null;
               this.removeAdjDir(adjacent, direction);
            }
      }
   }

   private void addAdjDir(RenderSection adjacent, int direction) {
      this.adjDirs |= (byte)(1 << direction);
      adjacent.adjDirs = (byte)(adjacent.adjDirs | (byte)(1 << Util.getOppositeDirIdx((byte)direction)));
   }

   private void removeAdjDir(RenderSection adjacent, int direction) {
      this.adjDirs &= (byte)(~(1 << direction));
      adjacent.adjDirs = (byte)(adjacent.adjDirs & (byte)(~(1 << Util.getOppositeDirIdx((byte)direction))));
   }

   public boolean resortTransparency(TaskDispatcher taskDispatcher, Vector3d cameraPos) {
      CompiledSection compiledSection = this.getCompiledSection();
      if (this.compileStatus.sortTask != null) {
         this.compileStatus.sortTask.cancel();
      }

      if (!compiledSection.hasTransparencyState()) {
         return false;
      }

      this.compileStatus.sortTask = ChunkTask.createSortTask(this, cameraPos);
      taskDispatcher.schedule(this.compileStatus.sortTask);
      return true;
   }

   public boolean rebuildChunkAsync(TaskDispatcher dispatcher, RenderRegionBuilder renderRegionCache, Vector3d cameraPos) {
      BuildTask chunkCompileTask = this.createCompileTask(renderRegionCache, cameraPos);
      if (chunkCompileTask == null) {
         return false;
      }

      dispatcher.schedule(chunkCompileTask);
      return true;
   }

   public void rebuildChunkSync(TaskDispatcher dispatcher, RenderRegionBuilder renderRegionCache) {
   }

   public BuildTask createCompileTask(RenderRegionBuilder renderRegionCache, Vector3d cameraPos) {
      boolean flag = this.cancelTasks();
      Level level = WorldRenderer.getLevel();
      int secX = this.xOffset >> 4;
      int secZ = this.zOffset >> 4;
      int secY = this.yOffset >> 4;
      if (!ChunkStatusMap.INSTANCE.chunkRenderReady(secX, secZ)) {
         return null;
      }

      RenderRegion renderRegion = renderRegionCache.createRegion(level, secX, secY, secZ);
      boolean flag1 = this.compileStatus.compiledSection == CompiledSection.UNCOMPILED;
      this.compileStatus.buildTask = ChunkTask.createBuildTask(this, renderRegion, cameraPos, !flag1 || flag);
      return this.compileStatus.buildTask;
   }

   protected boolean cancelTasks() {
      boolean flag = false;
      if (this.compileStatus.buildTask != null) {
         this.compileStatus.buildTask.cancel();
         this.compileStatus.buildTask = null;
         flag = true;
      }

      if (this.compileStatus.sortTask != null) {
         this.compileStatus.sortTask.cancel();
         this.compileStatus.sortTask = null;
      }

      return flag;
   }

   public void setNotDirty() {
      this.dirty = false;
      this.playerChanged = false;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public boolean isDirtyFromPlayer() {
      return this.dirty && this.playerChanged;
   }

   public int xOffset() {
      return this.xOffset;
   }

   public int yOffset() {
      return this.yOffset;
   }

   public int zOffset() {
      return this.zOffset;
   }

   public void resetDrawParameters(TerrainRenderType renderType) {
      for (int i = 0; i < QuadFacing.COUNT; i++) {
         DrawBuffers drawBuffers = this.chunkArea.getDrawBuffers();
         long ptr = DrawParametersBuffer.getParamsPtr(drawBuffers.getDrawParamsPtr(), this.inAreaIndex, renderType.ordinal(), i);
         AreaBuffer areaBuffer = drawBuffers.getAreaBuffer(renderType);
         int vertexOffset = DrawParametersBuffer.getVertexOffset(ptr);
         if (areaBuffer != null && vertexOffset != -1) {
            int segmentOffset = vertexOffset * drawBuffers.vertexSize;
            areaBuffer.setSegmentFree(segmentOffset);
         }

         DrawParametersBuffer.resetParameters(ptr);
      }
   }

   public void setChunkArea(ChunkArea chunkArea) {
      this.chunkArea = chunkArea;
      this.frustumIndex = chunkArea.getFrustumIndex(this.xOffset, this.yOffset, this.zOffset);
   }

   public ChunkArea getChunkArea() {
      return this.chunkArea;
   }

   public CompiledSection getCompiledSection() {
      return this.compileStatus.compiledSection;
   }

   public boolean isCompiled() {
      return this.compileStatus.compiledSection != CompiledSection.UNCOMPILED;
   }

   public void setVisibility(long visibility) {
      this.visibility = visibility;
   }

   public void setCompletelyEmpty(boolean b) {
      this.completelyEmpty = b;
   }

   public void setContainsBlockEntities(boolean b) {
      this.containsBlockEntities = b;
   }

   public byte getDirections() {
      return this.directions;
   }

   public byte getVisibilityDirs() {
      return (byte)(this.visibility >> (Util.getOppositeDirIdx(this.mainDir) << 3));
   }

   public long getVisibility() {
      return this.visibility;
   }

   public boolean isCompletelyEmpty() {
      return this.completelyEmpty;
   }

   public boolean containsBlockEntities() {
      return this.containsBlockEntities;
   }

   public void updateGlobalBlockEntities(Collection<BlockEntity> fullSet) {
      Set<BlockEntity> sectionSet = this.compileStatus.globalBlockEntities;
      if (sectionSet.size() != fullSet.size() || !sectionSet.containsAll(fullSet)) {
         Set<BlockEntity> toRemove = Sets.newHashSet(sectionSet);
         Set<BlockEntity> toAdd = Sets.newHashSet(fullSet);
         toAdd.removeAll(sectionSet);
         toRemove.removeAll(fullSet);
         sectionSet.clear();
         sectionSet.addAll(fullSet);
      }
   }

   private void reset() {
      this.cancelTasks();
      this.compileStatus.compiledSection = CompiledSection.UNCOMPILED;
      this.dirty = true;
      this.visibility = 0L;
      this.completelyEmpty = true;
      this.resetDrawParameters();
   }

   private void resetDrawParameters() {
      if (this.chunkArea != null) {
         long basePtr = this.chunkArea.getDrawBuffers().getDrawParamsPtr();

         for (TerrainRenderType renderType : TerrainRenderType.VALUES) {
            for (QuadFacing facing : QuadFacing.VALUES) {
               long ptr = DrawParametersBuffer.getParamsPtr(basePtr, this.inAreaIndex, renderType.ordinal(), facing.ordinal());
               DrawParametersBuffer.resetParameters(ptr);
            }
         }
      }
   }

   public void setDirty(boolean playerChanged) {
      this.playerChanged = playerChanged || this.dirty && this.playerChanged;
      this.dirty = true;
      WorldRenderer.getInstance().scheduleGraphUpdate();
   }

   public void setCompiledSection(CompiledSection compiledSection) {
      this.compileStatus.compiledSection = compiledSection;
   }

   public boolean setLastFrame(short i) {
      boolean alreadySet = i == this.lastFrame;
      if (!alreadySet) {
         this.lastFrame = i;
      }

      return alreadySet;
   }

   public boolean setLastFrame2(short i) {
      boolean alreadySet = i == this.lastFrame2;
      if (!alreadySet) {
         this.lastFrame2 = i;
      }

      return alreadySet;
   }

   public short getLastFrame() {
      return this.lastFrame;
   }

   public short getLastFrame2() {
      return this.lastFrame2;
   }

   static class CompileStatus {
      CompiledSection compiledSection = CompiledSection.UNCOMPILED;
      Set<BlockEntity> globalBlockEntities = new ObjectOpenHashSet();
      BuildTask buildTask;
      SortTransparencyTask sortTask;

      CompileStatus() {
      }
   }
}
