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
package com.xeno.render.engine;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.xeno.vulkan.shader.Pipeline;
import com.xeno.vulkan.shader.descriptor.ImageDescriptor;
import com.xeno.vulkan.shader.descriptor.UBO;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class EGlProgram {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static Set<String> BUILT_IN_UNIFORMS = Sets.newHashSet(new String[]{"Projection", "Lighting", "Fog", "Globals"});
   public static EGlProgram INVALID_PROGRAM = new EGlProgram(-1, "invalid");
   private final Map<String, Uniform> uniformsByName = new HashMap<>();
   private final int programId;
   private final String debugLabel;

   public EGlProgram(int i, String string) {
      this.programId = i;
      this.debugLabel = string;
   }

   public void setupUniforms(Pipeline pipeline, List<RenderPipeline.UniformDescription> uniformDescriptions, List<String> samplers) {
      int i = 0;
      int j = 0;

      for (RenderPipeline.UniformDescription uniformDescription : uniformDescriptions) {
         String name = uniformDescription.name();

         Uniform uniform = switch (uniformDescription.type()) {
            case UNIFORM_BUFFER -> {
               UBO ubo = pipeline.getUBO(name);
               if (ubo == null) {
                  yield null;
               } else {
                  int binding = ubo.binding;
                  yield new Uniform.Ubo(binding);
               }
            }
            case TEXEL_BUFFER -> {
               int binding = i++;
               yield new Uniform.Utb(binding, 0, Objects.requireNonNull(uniformDescription.textureFormat()));
            }
         };
         this.uniformsByName.put(name, uniform);
      }

      for (String samplerName : samplers) {
         ImageDescriptor imageDescriptor = pipeline.getImageDescriptor(samplerName);
         if (imageDescriptor != null) {
            int binding = imageDescriptor.getBinding();
            int imageIdx = imageDescriptor.imageIdx;
            this.uniformsByName.put(samplerName, new Uniform.Sampler(binding, imageIdx));
         }
      }
   }

   @Nullable
   public Uniform getUniform(String string) {
      RenderSystem.assertOnRenderThread();
      return this.uniformsByName.get(string);
   }

   public int getProgramId() {
      return this.programId;
   }

   @Override
   public String toString() {
      return this.debugLabel;
   }

   public String getDebugLabel() {
      return this.debugLabel;
   }

   public Map<String, Uniform> getUniforms() {
      return this.uniformsByName;
   }
}
