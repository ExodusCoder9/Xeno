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

public class VkGlShader {
   private static int ID_COUNTER = 1;
   private static final Int2ReferenceOpenHashMap<VkGlShader> map = new Int2ReferenceOpenHashMap();
   private static int boundTextureId = 0;
   final int id;
   final int type;
   String source;

   public static int glCreateShader(int type) {
      int id = ID_COUNTER++;
      VkGlShader shader = new VkGlShader(id, type);
      map.put(id, shader);
      return id;
   }

   public static void glDeleteShader(int i) {
      map.remove(i);
   }

   public static void glShaderSource(int i, String string) {
      VkGlShader shader = (VkGlShader)map.get(i);
      shader.source = string;
   }

   public static void glCompileShader(int i) {
   }

   public static int glGetShaderi(int i, int j) {
      return 0;
   }

   VkGlShader(int id, int type) {
      this.id = id;
      this.type = type;
   }
}
