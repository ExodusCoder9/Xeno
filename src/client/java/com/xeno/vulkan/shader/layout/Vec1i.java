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

import java.util.function.Supplier;
import com.xeno.vulkan.util.MappedBuffer;
import com.xeno.vulkan.util.MemoryAccess;

public class Vec1i extends Uniform {
   private Supplier<Integer> intSupplier;

   public Vec1i(Uniform.Info info) {
      super(info);
   }

   @Override
   protected void setupSupplier() {
      if (this.info.intSupplier != null) {
         this.intSupplier = this.info.intSupplier;
      } else if (this.info.bufferSupplier != null) {
         this.setSupplier(this.info.bufferSupplier);
      }
   }

   @Override
   public void setSupplier(Supplier<MappedBuffer> supplier) {
      this.intSupplier = () -> supplier.get().getInt(0);
   }

   @Override
   void update(long ptr) {
      if (this.intSupplier != null) {
         int i = this.intSupplier.get();
         MemoryAccess.memPutInt(ptr + this.offset, i);
      }
   }
}
