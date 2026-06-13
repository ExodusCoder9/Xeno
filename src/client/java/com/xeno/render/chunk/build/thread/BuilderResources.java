package com.xeno.render.chunk.build.thread;

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


import com.xeno.Initializer;
import com.xeno.render.chunk.RenderSection;
import com.xeno.render.chunk.build.RenderRegion;
import com.xeno.render.chunk.build.color.TintCache;
import com.xeno.render.chunk.build.light.LightPipeline;
import com.xeno.render.chunk.build.light.data.ArrayLightDataCache;
import com.xeno.render.chunk.build.light.data.QuadLightData;
import com.xeno.render.chunk.build.light.flat.FlatLightPipeline;
import com.xeno.render.chunk.build.light.smooth.NewSmoothLightPipeline;
import com.xeno.render.chunk.build.light.smooth.SmoothLightPipeline;
import com.xeno.render.chunk.build.renderer.BlockRenderer;
import com.xeno.render.chunk.build.renderer.FluidRenderer;

public class BuilderResources {
   public final ThreadBuilderPack builderPack = new ThreadBuilderPack();
   public final TintCache tintCache = new TintCache();
   public final BlockRenderer blockRenderer;
   public final FluidRenderer fluidRenderer;
   public final ArrayLightDataCache lightDataCache = new ArrayLightDataCache();
   public final QuadLightData quadLightData = new QuadLightData();
   private RenderRegion region;
   private int totalBuildTime = 0;
   private int buildCount = 0;

   public BuilderResources() {
      LightPipeline flatLightPipeline = new FlatLightPipeline(this.lightDataCache);
      LightPipeline smoothLightPipeline;
      if (Initializer.CONFIG.ambientOcclusion == 2) {
         smoothLightPipeline = new NewSmoothLightPipeline(this.lightDataCache);
      } else {
         smoothLightPipeline = new SmoothLightPipeline(this.lightDataCache);
      }

      this.blockRenderer = new BlockRenderer(flatLightPipeline, smoothLightPipeline);
      this.fluidRenderer = new FluidRenderer(flatLightPipeline, smoothLightPipeline);
      this.blockRenderer.setResources(this);
      this.fluidRenderer.setResources(this);
   }

   public void update(RenderRegion region, RenderSection renderSection) {
      this.region = region;
      this.blockRenderer.prepareForWorld(region, true);
      this.lightDataCache.reset(region, renderSection.xOffset(), renderSection.yOffset(), renderSection.zOffset());
   }

   public void free() {
      this.builderPack.freeAll();
   }

   public void updateBuildStats(int buildTime) {
      this.buildCount++;
      this.totalBuildTime += buildTime;
   }

   public RenderRegion getRegion() {
      return this.region;
   }

   public int getTotalBuildTime() {
      return this.totalBuildTime;
   }

   public int getBuildCount() {
      return this.buildCount;
   }

   public void resetCounters() {
      this.totalBuildTime = 0;
      this.buildCount = 0;
   }
}
