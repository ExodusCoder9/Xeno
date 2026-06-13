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

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalDouble;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.xeno.vulkan.texture.SamplerManager;

@Environment(EnvType.CLIENT)
public class VkSampler extends GpuSampler {
   private final AddressMode addressModeU;
   private final AddressMode addressModeV;
   private final FilterMode minFilter;
   private final FilterMode magFilter;
   private final int maxAnisotropy;
   private final float maxLod;
   private boolean closed;
   private final long id;

   public VkSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy, OptionalDouble maxLod) {
      this.addressModeU = addressModeU;
      this.addressModeV = addressModeV;
      this.minFilter = minFilter;
      this.magFilter = magFilter;
      this.maxAnisotropy = maxAnisotropy;
      this.maxLod = maxLod.isPresent() ? (byte)maxLod.getAsDouble() : 1000.0F;
      this.id = SamplerManager.getSampler(
         VkConst.of(addressModeU), VkConst.of(addressModeV), VkConst.of(minFilter), VkConst.of(magFilter), 1, this.maxLod, maxAnisotropy > 1, maxAnisotropy, -1
      );
   }

   public long getId() {
      return this.id;
   }

   @Override
   public AddressMode getAddressModeU() {
      return this.addressModeU;
   }

   @Override
   public AddressMode getAddressModeV() {
      return this.addressModeV;
   }

   @Override
   public FilterMode getMinFilter() {
      return this.minFilter;
   }

   @Override
   public FilterMode getMagFilter() {
      return this.magFilter;
   }

   @Override
   public int getMaxAnisotropy() {
      return this.maxAnisotropy;
   }

   @Override
   public OptionalDouble getMaxLod() {
      return null;
   }

   @Override
   public void close() {
      if (!this.closed) {
         this.closed = true;
      }
   }

   public boolean isClosed() {
      return this.closed;
   }
}
