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
package com.xeno.mixin.render.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.xeno.interfaces.shader.ExtendedRenderPipeline;
import com.xeno.render.engine.EGlProgram;
import com.xeno.vulkan.shader.GraphicsPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineM implements ExtendedRenderPipeline {
   @Unique
   GraphicsPipeline pipeline;
   @Unique
   EGlProgram eGlProgram;

   @Override
   public void setPipeline(GraphicsPipeline pipeline) {
      this.pipeline = pipeline;
   }

   @Override
   public void setProgram(EGlProgram program) {
      this.eGlProgram = program;
   }

   @Override
   public EGlProgram getProgram() {
      return this.eGlProgram;
   }

   @Override
   public GraphicsPipeline getPipeline() {
      return this.pipeline;
   }
}
