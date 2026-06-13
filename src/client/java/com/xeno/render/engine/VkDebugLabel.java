package com.xeno.render.engine;

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


import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.VertexArrayCache.VertexArray;
import com.mojang.logging.LogUtils;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class VkDebugLabel {
   private static final Logger LOGGER = LogUtils.getLogger();

   public VkDebugLabel() {
   }

   public void applyLabel(VkGpuBuffer glBuffer) {
   }

   public void applyLabel(VkGpuTexture glTexture) {
   }

   public void applyLabel(GlShaderModule glShaderModule) {
   }

   public void applyLabel(GlProgram glProgram) {
   }

   public void applyLabel(VertexArray vertexArray) {
   }

   public static VkDebugLabel create(boolean bl, Set<String> set) {
      return new VkDebugLabel();
   }

   public boolean exists() {
      return true;
   }
}
