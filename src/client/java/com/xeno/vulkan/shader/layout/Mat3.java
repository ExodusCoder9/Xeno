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
package com.xeno.vulkan.shader.layout;

import com.xeno.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryUtil;

public class Mat3 extends Uniform {
   Mat3(Uniform.Info info) {
      super(info);
   }

   @Override
   void update(long ptr) {
      MappedBuffer src = this.values.get();
      MemoryUtil.memCopy(src.ptr + 0L, ptr + this.offset + 0L, 12L);
      MemoryUtil.memCopy(src.ptr + 12L, ptr + this.offset + 16L, 12L);
      MemoryUtil.memCopy(src.ptr + 24L, ptr + this.offset + 32L, 12L);
   }
}
