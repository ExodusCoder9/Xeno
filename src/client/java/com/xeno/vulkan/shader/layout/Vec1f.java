package com.xeno.vulkan.shader.layout;

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


import java.util.function.Supplier;
import com.xeno.vulkan.util.MappedBuffer;
import com.xeno.vulkan.util.MemoryAccess;

public class Vec1f extends Uniform {
   private Supplier<Float> floatSupplier;

   public Vec1f(Uniform.Info info) {
      super(info);
   }

   @Override
   protected void setupSupplier() {
      if (this.info.floatSupplier != null) {
         this.floatSupplier = this.info.floatSupplier;
      } else if (this.info.bufferSupplier != null) {
         this.setSupplier(this.info.bufferSupplier);
      }
   }

   @Override
   public void setSupplier(Supplier<MappedBuffer> supplier) {
      this.floatSupplier = () -> supplier.get().getFloat(0);
   }

   @Override
   void update(long ptr) {
      if (this.floatSupplier != null) {
         float f = this.floatSupplier.get();
         MemoryAccess.memPutFloat(ptr + this.offset, f);
      }
   }
}
