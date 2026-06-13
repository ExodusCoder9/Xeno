package com.xeno.render.shader;

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


import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;

public class CustomRenderPipelines {
   public static final List<RenderPipeline> pipelines = new ArrayList<>();
   public static final Snippet GUI_TRIANGLES_SNIPPET = RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_PROJECTION_SNIPPET})
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLES)
      .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
      .buildSnippet();
   public static final RenderPipeline GUI_TRIANGLES = register(
      RenderPipeline.builder(new Snippet[]{GUI_TRIANGLES_SNIPPET}).withLocation("pipeline/gui").build()
   );

   public CustomRenderPipelines() {
   }

   static RenderPipeline register(RenderPipeline pipeline) {
      pipelines.add(pipeline);
      return pipeline;
   }
}
