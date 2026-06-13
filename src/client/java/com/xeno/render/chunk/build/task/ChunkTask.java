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


import java.util.concurrent.atomic.AtomicBoolean;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.build.RenderRegion;
import com.xeno.render.chunk.build.thread.BuilderResources;
import org.joml.Vector3d;

public abstract class ChunkTask {
   public static final boolean BENCH = true;
   protected static TaskDispatcher taskDispatcher;
   protected final RenderSection section;
   protected final Vector3d cameraPos;
   protected AtomicBoolean cancelled = new AtomicBoolean(false);
   public boolean highPriority = false;

   public static BuildTask createBuildTask(RenderSection renderSection, RenderRegion renderRegion, Vector3d cameraPos, boolean highPriority) {
      return new BuildTask(renderSection, renderRegion, cameraPos, highPriority);
   }

   public static SortTransparencyTask createSortTask(RenderSection renderSection, Vector3d cameraPos) {
      return new SortTransparencyTask(renderSection, cameraPos);
   }

   ChunkTask(RenderSection renderSection, Vector3d cameraPos) {
      this.section = renderSection;
      this.cameraPos = cameraPos;
   }

   public abstract String name();

   public abstract ChunkTask.Result runTask(BuilderResources var1);

   public void cancel() {
      this.cancelled.set(true);
   }

   public static void setTaskDispatcher(TaskDispatcher dispatcher) {
      taskDispatcher = dispatcher;
   }

   public enum Result {
      CANCELLED,
      SUCCESSFUL;

      Result() {
      }
   }
}
