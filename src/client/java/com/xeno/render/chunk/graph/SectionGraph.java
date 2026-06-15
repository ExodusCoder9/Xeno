package com.xeno.render.chunk.graph;

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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.FrustumIntersection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.xeno.Initializer;
import com.xeno.interfaces.FrustumMixed;
import com.xeno.render.chunk.ChunkArea;
import com.xeno.render.chunk.ChunkAreaManager;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.SectionGrid;
import com.xeno.render.chunk.WorldRenderer;
import com.xeno.render.chunk.build.RenderRegionBuilder;
import com.xeno.render.chunk.build.task.TaskDispatcher;
import com.xeno.render.chunk.frustum.VFrustum;
import com.xeno.render.chunk.util.AreaSetQueue;
import com.xeno.render.chunk.util.ResettableQueue;
import com.xeno.render.chunk.util.StaticQueue;
import com.xeno.render.profiling.Profiler;
import org.joml.Vector3d;

public class SectionGraph {
   Minecraft minecraft;
   private final Level level;
   private final SectionGrid sectionGrid;
   private final ChunkAreaManager chunkAreaManager;
   private final TaskDispatcher taskDispatcher;
   private final ResettableQueue<RenderSection> sectionQueue = new ResettableQueue<>();
   private AreaSetQueue chunkAreaQueue;
   private short lastFrame = 0;
   private final ResettableQueue<RenderSection> blockEntitiesSections = new ResettableQueue<>();
   private final ResettableQueue<RenderSection> rebuildQueue = new ResettableQueue<>();
   private VFrustum frustum;
   public RenderRegionBuilder renderRegionCache;
   int nonEmptyChunks;
   // Pre-computed constant: bits 48-53 (dirs 0-5 at offset 48) and bits 56-61 (dirs 0-5 at offset 56)
   private static final long INIT_VISIBILITY;
   static {
      long vis = 0L;
      for (int dir = 0; dir < 6; dir++) {
         vis |= 1L << 48 + dir;
         vis |= 1L << 56 + dir;
      }
      INIT_VISIBILITY = vis;
   }

   public SectionGraph(Level level, SectionGrid sectionGrid, TaskDispatcher taskDispatcher) {
      this.level = level;
      this.sectionGrid = sectionGrid;
      this.chunkAreaManager = sectionGrid.getChunkAreaManager();
      this.taskDispatcher = taskDispatcher;
      this.chunkAreaQueue = new AreaSetQueue(sectionGrid.getChunkAreaManager().size);
      this.minecraft = Minecraft.getInstance();
      this.renderRegionCache = WorldRenderer.getInstance().renderRegionCache;
   }

   public void update(Camera camera, Frustum frustum, boolean spectator) {
      Profiler profiler = Profiler.getMainProfiler();
      ProfilerFiller mcProfiler = net.minecraft.util.profiling.Profiler.get();
      BlockPos blockpos = camera.blockPosition();
      mcProfiler.popPush("update");
      boolean flag = this.minecraft.smartCull;
      if (spectator && this.level.getBlockState(blockpos).isSolidRender()) {
         flag = false;
      }

      profiler.push("frustum");
      this.frustum = ((FrustumMixed)frustum).customFrustum().offsetToFullyIncludeCameraCube(8);
      this.sectionGrid.updateFrustumVisibility(this.frustum);
      profiler.pop();
      mcProfiler.push("partial_update");
      this.initUpdate();
      this.initializeQueueForFullUpdate(camera);
      if (flag) {
         this.updateRenderChunks();
      } else {
         this.updateRenderChunksSpectator();
      }

      this.scheduleRebuilds();
      mcProfiler.pop();
   }

   private void initializeQueueForFullUpdate(Camera camera) {
      Vec3 vec3 = camera.position();
      BlockPos blockpos = camera.blockPosition();
      RenderSection renderSection = this.sectionGrid.getSectionAtBlockPos(blockpos);
      if (renderSection == null) {
         boolean flag = blockpos.getY() > this.level.getMinY();
         int y = flag ? this.level.getMaxY() - 8 : this.level.getMinY() + 8;
         int x = Mth.floor(vec3.x / 16.0) * 16;
         int z = Mth.floor(vec3.z / 16.0) * 16;
         List<RenderSection> list = Lists.newArrayList();
         int renderDistance = WorldRenderer.getInstance().getRenderDistance();

         for (int x1 = -renderDistance; x1 <= renderDistance; x1++) {
            for (int z1 = -renderDistance; z1 <= renderDistance; z1++) {
               RenderSection renderSection1 = this.sectionGrid
                  .getSectionAtBlockPos(x + SectionPos.sectionToBlockCoord(x1, 8), y, z + SectionPos.sectionToBlockCoord(z1, 8));
               if (renderSection1 != null) {
                  initFirstNode(renderSection1, this.lastFrame);
                  list.add(renderSection1);
               }
            }
         }

         this.sectionQueue.ensureCapacity(list.size());

         for (RenderSection chunkInfo : list) {
            this.sectionQueue.add(chunkInfo);
         }
      } else {
         initFirstNode(renderSection, this.lastFrame);
         this.sectionQueue.add(renderSection);
      }
   }

   private static void initFirstNode(RenderSection renderSection, short frame) {
      renderSection.mainDir = 7;
      renderSection.sourceDirs = -128;
      renderSection.directions = -1;
      renderSection.setLastFrame(frame);
      renderSection.visibility = renderSection.visibility | initVisibility();
      renderSection.directionChanges = 0;
      renderSection.steps = 0;
   }

   private static long initVisibility() {
      return INIT_VISIBILITY;
   }

   private void initUpdate() {
      int count = this.sectionGrid.getSectionCount();
      this.sectionQueue.ensureCapacity(count);
      this.blockEntitiesSections.ensureCapacity(count);
      this.rebuildQueue.ensureCapacity(count);
      this.resetUpdateQueues();
      this.lastFrame++;
      this.nonEmptyChunks = 0;
   }

   private void resetUpdateQueues() {
      StaticQueue<ChunkArea> activeQueue = this.chunkAreaQueue.queue();
      int activeSize = activeQueue.size();
      for (int i = 0; i < activeSize; i++) {
         activeQueue.get(i).resetQueue();
      }
      this.chunkAreaQueue.clear();

      this.sectionQueue.clear();
      this.blockEntitiesSections.clear();
      this.rebuildQueue.clear();
   }

    private void updateRenderChunks() {
       int maxDirectionsChanges = Initializer.CONFIG.advCulling - 1;
       float camX = this.frustum.camXf;
       float camY = this.frustum.camYf;
       float camZ = this.frustum.camZf;

       while (this.sectionQueue.hasNext()) {
          RenderSection renderSection = this.sectionQueue.poll();
          if (!this.notInFrustum(renderSection, camX, camY, camZ) && renderSection.directionChanges <= maxDirectionsChanges) {
             if (!renderSection.completelyEmpty) {
                renderSection.chunkArea.sectionQueue.add(renderSection);
                this.chunkAreaQueue.add(renderSection.chunkArea);
                this.nonEmptyChunks++;
             }

             if (renderSection.containsBlockEntities) {
                this.blockEntitiesSections.add(renderSection);
             }

             if (renderSection.dirty) {
                this.rebuildQueue.add(renderSection);
             }

             byte dirs = (byte)(((renderSection.visibility >> ((renderSection.mainDir ^ 1) << 3)) & renderSection.directions));
             this.visitAdjacentNodes(renderSection, dirs);
          }
       }
    }

    private void scheduleRebuilds() {
       int size = this.rebuildQueue.size();
       Vector3d cameraPos = WorldRenderer.getCameraPos();
       for (int i = 0; i < size; i++) {
          RenderSection section = this.rebuildQueue.get(i);
          section.rebuildChunkAsync(this.taskDispatcher, this.renderRegionCache, cameraPos);
          section.setNotDirty();
       }

       this.rebuildQueue.clear();
    }

    private boolean notInFrustum(RenderSection renderSection, float camX, float camY, float camZ) {
       byte frustumRes = renderSection.chunkArea.frustumBuffer[renderSection.frustumIndex];
       if (frustumRes == FrustumIntersection.OUTSIDE) {
          return true;
       }
       if (frustumRes == FrustumIntersection.INTERSECT) {
          return !this.frustum
             .testFrustumRelative(
                renderSection.xOffsetF - camX,
                renderSection.yOffsetF - camY,
                renderSection.zOffsetF - camZ,
                renderSection.xOffsetEndF - camX,
                renderSection.yOffsetEndF - camY,
                renderSection.zOffsetEndF - camZ
             );
       }
       return false;
    }

    private void visitAdjacentNodes(RenderSection renderSection, byte dirs) {
       dirs = (byte)(dirs & renderSection.adjDirs);
       if (dirs == 0) {
          return;
       }
       boolean renderNotCompletelyEmpty = !renderSection.completelyEmpty;
       byte stepsPlusOne = (byte)(renderSection.steps + 1);
       byte renderDirChanges = renderSection.directionChanges;
       byte renderDirChangesPlusOne = (byte)(renderDirChanges + 1);
       int renderSourceDirs = renderSection.sourceDirs;
       int renderDirections = renderSection.directions;

       if ((dirs & 1) != 0) {
          RenderSection rel = renderSection.adjDown;
          if (rel.lastFrame != this.lastFrame) {
             rel.lastFrame = this.lastFrame;
             rel.mainDir = 0;
             rel.sourceDirs = 1;
             rel.steps = stepsPlusOne;
             rel.directionChanges = (byte)(stepsPlusOne < 10 ? 0 : 127);
             rel.directions = (byte)(renderDirections & ~2);
             this.sectionQueue.add(rel);
          }
          rel.sourceDirs |= 1;
          boolean increase = (renderSourceDirs & 1) == 0 && renderNotCompletelyEmpty;
          byte dc = increase ? renderDirChangesPlusOne : renderDirChanges;
          if (dc < rel.directionChanges) {
             rel.directionChanges = dc;
          }
       }
       if ((dirs & 2) != 0) {
          RenderSection rel = renderSection.adjUp;
          if (rel.lastFrame != this.lastFrame) {
             rel.lastFrame = this.lastFrame;
             rel.mainDir = 1;
             rel.sourceDirs = 2;
             rel.steps = stepsPlusOne;
             rel.directionChanges = (byte)(stepsPlusOne < 10 ? 0 : 127);
             rel.directions = (byte)(renderDirections & ~1);
             this.sectionQueue.add(rel);
          }
          rel.sourceDirs |= 2;
          boolean increase = (renderSourceDirs & 2) == 0 && renderNotCompletelyEmpty;
          byte dc = increase ? renderDirChangesPlusOne : renderDirChanges;
          if (dc < rel.directionChanges) {
             rel.directionChanges = dc;
          }
       }
       if ((dirs & 4) != 0) {
          RenderSection rel = renderSection.adjNorth;
          if (rel.lastFrame != this.lastFrame) {
             rel.lastFrame = this.lastFrame;
             rel.mainDir = 2;
             rel.sourceDirs = 4;
             rel.steps = stepsPlusOne;
             rel.directionChanges = (byte)(stepsPlusOne < 10 ? 0 : 127);
             rel.directions = (byte)(renderDirections & ~8);
             this.sectionQueue.add(rel);
          }
          rel.sourceDirs |= 4;
          boolean increase = (renderSourceDirs & 4) == 0 && renderNotCompletelyEmpty;
          byte dc = increase ? renderDirChangesPlusOne : renderDirChanges;
          if (dc < rel.directionChanges) {
             rel.directionChanges = dc;
          }
       }
       if ((dirs & 8) != 0) {
          RenderSection rel = renderSection.adjSouth;
          if (rel.lastFrame != this.lastFrame) {
             rel.lastFrame = this.lastFrame;
             rel.mainDir = 3;
             rel.sourceDirs = 8;
             rel.steps = stepsPlusOne;
             rel.directionChanges = (byte)(stepsPlusOne < 10 ? 0 : 127);
             rel.directions = (byte)(renderDirections & ~4);
             this.sectionQueue.add(rel);
          }
          rel.sourceDirs |= 8;
          boolean increase = (renderSourceDirs & 8) == 0 && renderNotCompletelyEmpty;
          byte dc = increase ? renderDirChangesPlusOne : renderDirChanges;
          if (dc < rel.directionChanges) {
             rel.directionChanges = dc;
          }
       }
       if ((dirs & 16) != 0) {
          RenderSection rel = renderSection.adjWest;
          if (rel.lastFrame != this.lastFrame) {
             rel.lastFrame = this.lastFrame;
             rel.mainDir = 4;
             rel.sourceDirs = 16;
             rel.steps = stepsPlusOne;
             rel.directionChanges = (byte)(stepsPlusOne < 10 ? 0 : 127);
             rel.directions = (byte)(renderDirections & ~32);
             this.sectionQueue.add(rel);
          }
          rel.sourceDirs |= 16;
          boolean increase = (renderSourceDirs & 16) == 0 && renderNotCompletelyEmpty;
          byte dc = increase ? renderDirChangesPlusOne : renderDirChanges;
          if (dc < rel.directionChanges) {
             rel.directionChanges = dc;
          }
       }
       if ((dirs & 32) != 0) {
          RenderSection rel = renderSection.adjEast;
          if (rel.lastFrame != this.lastFrame) {
             rel.lastFrame = this.lastFrame;
             rel.mainDir = 5;
             rel.sourceDirs = 32;
             rel.steps = stepsPlusOne;
             rel.directionChanges = (byte)(stepsPlusOne < 10 ? 0 : 127);
             rel.directions = (byte)(renderDirections & ~16);
             this.sectionQueue.add(rel);
          }
          rel.sourceDirs |= 32;
          boolean increase = (renderSourceDirs & 32) == 0 && renderNotCompletelyEmpty;
          byte dc = increase ? renderDirChangesPlusOne : renderDirChanges;
          if (dc < rel.directionChanges) {
             rel.directionChanges = dc;
          }
       }
    }

    private void updateRenderChunksSpectator() {
       float camX = this.frustum.camXf;
       float camY = this.frustum.camYf;
       float camZ = this.frustum.camZf;

       while (this.sectionQueue.hasNext()) {
          RenderSection renderSection = this.sectionQueue.poll();
          if (!this.notInFrustum(renderSection, camX, camY, camZ)) {
             if (!renderSection.completelyEmpty) {
                renderSection.chunkArea.sectionQueue.add(renderSection);
                this.chunkAreaQueue.add(renderSection.chunkArea);
                this.nonEmptyChunks++;
             }

             if (renderSection.dirty) {
                this.rebuildQueue.add(renderSection);
             }

             byte dirs = (byte)(renderSection.adjDirs & renderSection.directions);
             this.visitAdjacentNodes(renderSection, dirs);
          }
       }
    }

   public AreaSetQueue getChunkAreaQueue() {
      return this.chunkAreaQueue;
   }

   public ResettableQueue<RenderSection> getSectionQueue() {
      return this.sectionQueue;
   }

   public ResettableQueue<RenderSection> getBlockEntitiesSections() {
      return this.blockEntitiesSections;
   }

   public short getLastFrame() {
      return this.lastFrame;
   }

   public String getStatistics() {
      int totalSections = this.sectionGrid.getSectionCount();
      int sections = this.sectionQueue.size();
      int renderDistance = WorldRenderer.getInstance().getRenderDistance();
      String tasksInfo = this.taskDispatcher == null ? "null" : this.taskDispatcher.getStats();
      return String.format("Chunks: %d(%d)/%d D: %d, %s", this.nonEmptyChunks, sections, totalSections, renderDistance, tasksInfo);
   }
}
