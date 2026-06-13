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
package com.xeno.vulkan.shader.descriptor;

import org.lwjgl.system.MemoryUtil;

public class ManualUBO extends UBO {
   private long srcPtr;
   private int srcSize;
   private boolean update = true;

   public ManualUBO(int binding, int type, int size) {
      super("manual UBO: %d".formatted(binding), binding, type, size * 4, null);
   }

   @Override
   public void update(long ptr) {
      if (this.update) {
         MemoryUtil.memCopy(this.srcPtr, ptr, this.srcSize);
      }
   }

   public void setSrc(long ptr, int size) {
      this.srcPtr = ptr;
      this.srcSize = size;
   }

   @Override
   public void setUpdate(boolean update) {
      this.update = update;
   }
}
