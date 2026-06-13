package com.xeno.vulkan.shader.descriptor;

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


import java.util.List;
import com.xeno.vulkan.memory.buffer.BufferSlice;
import com.xeno.vulkan.shader.layout.AlignedStruct;
import com.xeno.vulkan.shader.layout.Uniform;

public class UBO extends AlignedStruct implements Descriptor {
   public final String name;
   public final int binding;
   public final int stages;
   public final BufferSlice bufferSlice;
   private boolean useGlobalBuffer;
   private boolean update;

   public UBO(String name, int binding, int stages, int size, List<Uniform.Info> infoList) {
      super(infoList, size);
      this.name = name;
      this.binding = binding;
      this.stages = stages;
      this.update = true;
      this.bufferSlice = new BufferSlice();
   }

   @Override
   public String toString() {
      return "UBO{name='" + this.name + "', binding=" + this.binding + ", useGlobalBuffer=" + this.useGlobalBuffer + "}";
   }

   @Override
   public int getBinding() {
      return this.binding;
   }

   @Override
   public int getType() {
      return 8;
   }

   @Override
   public int getStages() {
      return this.stages;
   }

   public BufferSlice getBufferSlice() {
      return this.bufferSlice;
   }

   public boolean useGlobalBuffer() {
      return this.useGlobalBuffer;
   }

   public void setUseGlobalBuffer(boolean useGlobalBuffer) {
      this.useGlobalBuffer = useGlobalBuffer;
   }

   public boolean shouldUpdate() {
      return this.update;
   }

   public void setUpdate(boolean update) {
      this.update = update;
   }
}
