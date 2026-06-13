package com.xeno.gl;

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


import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import com.xeno.vulkan.shader.Pipeline;

public class VkGlProgram {
   private static int ID_COUNTER = 1;
   private static final Int2ReferenceOpenHashMap<VkGlProgram> map = new Int2ReferenceOpenHashMap();
   private static int boundProgramId = 0;
   private static VkGlProgram boundProgram;
   int id;
   Pipeline pipeline;

   public static VkGlProgram getBoundProgram() {
      return boundProgram;
   }

   public static VkGlProgram getProgram(int id) {
      return (VkGlProgram)map.get(id);
   }

   public static int genProgramId() {
      int id = ID_COUNTER;
      map.put(id, new VkGlProgram(id));
      ID_COUNTER++;
      return id;
   }

   public static void glUseProgram(int id) {
      boundProgramId = id;
      boundProgram = (VkGlProgram)map.get(id);
      if (id > 0) {
         if (boundProgram == null) {
            throw new NullPointerException("bound texture is null");
         }
      }
   }

   VkGlProgram(int i) {
      this.id = i;
   }

   public void bindPipeline(Pipeline pipeline) {
      this.pipeline = pipeline;
   }

   public Pipeline getPipeline() {
      return this.pipeline;
   }
}
