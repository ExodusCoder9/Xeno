package com.xeno.vulkan.texture;

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


public class SamplerInfo {
   final int encodedState;
   final int maxLod;
   final int maxAnisotropy;

   public SamplerInfo() {
      this(0, 0, 0, 0, 0, 0.0F, false, 0.0F, -1);
   }

   public SamplerInfo(
      int addressModeU,
      int addressModeV,
      int minFilter,
      int magFilter,
      int mipmapMode,
      float maxLod,
      boolean anisotropy,
      float maxAnisotropy,
      int reductionMode
   ) {
      this(addressModeU, addressModeV, minFilter, magFilter, mipmapMode, maxLod, anisotropy, maxAnisotropy, false, 0, reductionMode);
   }

   public SamplerInfo(
      int addressModeU,
      int addressModeV,
      int minFilter,
      int magFilter,
      int mipmapMode,
      float maxLod,
      boolean anisotropy,
      float maxAnisotropy,
      boolean compare,
      int compareOp,
      int reductionMode
   ) {
      this.maxLod = (int)maxLod;
      this.maxAnisotropy = (int)maxAnisotropy;
      this.encodedState = SamplerManager.getEncodedState(
         addressModeU, addressModeV, minFilter, magFilter, mipmapMode, anisotropy, compare, compareOp, reductionMode
      );
   }

   public int getAddressModeU() {
      return this.encodedState >> 0 & 2;
   }

   public int getAddressModeV() {
      return this.encodedState >> 2 & 2;
   }

   public int getMinFilter() {
      return this.encodedState >> 4 & 1;
   }

   public int getMagFilter() {
      return this.encodedState >> 5 & 1;
   }

   public int getMipmapMode() {
      return this.encodedState >> 6 & 1;
   }

   public boolean getAnisotropy() {
      return (this.encodedState >> 7 & 1) != 0;
   }

   public boolean compareEnabled() {
      return (this.encodedState >> 8 & 1) != 0;
   }

   public int getCompareOp() {
      return this.encodedState >> 9 & 3;
   }

   public boolean hasReductionMode() {
      return (this.encodedState >> 12 & 1) != 0;
   }

   public int getReductionMode() {
      return this.encodedState >> 13 & 2;
   }

   public int getMaxAnisotropy() {
      return this.maxAnisotropy;
   }

   public int getMaxLod() {
      return this.maxLod;
   }

   @Override
   public boolean equals(Object o) {
      if (o != null && this.getClass() == o.getClass()) {
         SamplerInfo samplerInfo = (SamplerInfo)o;
         return this.maxLod == samplerInfo.maxLod && this.maxAnisotropy == samplerInfo.maxAnisotropy && this.encodedState == samplerInfo.encodedState;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int result = this.encodedState;
      result = 31 * result + this.maxLod;
      return 31 * result + this.maxAnisotropy;
   }

   public static SamplerInfo.Builder builder() {
      return new SamplerInfo.Builder();
   }

   public static class Builder {
      int addressModeU = 2;
      int addressModeV = 2;
      int minFilter = 1;
      int magFilter = 1;
      int mipmapMode = 1;
      float maxLod = 0.0F;
      boolean anisotropy = false;
      float maxAnisotropy = 0.0F;
      boolean compareEnabled = false;
      int compareOp = 7;
      int reductionMode = 0;

      Builder() {
      }

      public SamplerInfo.Builder setAddressMode(int addressMode) {
         this.addressModeU = addressMode;
         this.addressModeV = addressMode;
         return this;
      }

      public SamplerInfo.Builder setFiltering(int minFilter, int magFilter, int mipmapMode) {
         this.minFilter = minFilter;
         this.magFilter = magFilter;
         this.mipmapMode = mipmapMode;
         return this;
      }

      public SamplerInfo.Builder setMaxLod(float maxLod) {
         this.maxLod = maxLod;
         return this;
      }

      public SamplerInfo.Builder setAnisotropy(float maxAnisotropy) {
         this.anisotropy = true;
         this.maxAnisotropy = maxAnisotropy;
         return this;
      }

      public SamplerInfo.Builder setCompare(boolean enable, int compareOp) {
         this.compareEnabled = enable;
         this.compareOp = compareOp;
         return this;
      }

      public SamplerInfo.Builder setReductionMode(int reductionMode) {
         this.reductionMode = reductionMode;
         return this;
      }

      public SamplerInfo createSamplerInfo() {
         return new SamplerInfo(
            this.addressModeU,
            this.addressModeV,
            this.minFilter,
            this.magFilter,
            this.mipmapMode,
            this.maxLod,
            this.anisotropy,
            this.maxAnisotropy,
            this.compareEnabled,
            this.compareOp,
            this.reductionMode
         );
      }
   }
}
