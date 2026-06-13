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

import com.xeno.vulkan.texture.VTextureSelector;
import com.xeno.vulkan.texture.VulkanImage;

public class ImageDescriptor implements Descriptor {
   private final int descriptorType;
   private final int binding;
   public final String qualifier;
   public final String name;
   public final int imageIdx;
   public boolean useSampler;
   public boolean isReadOnlyLayout;
   private int layout;
   private int mipLevel = -1;

   public ImageDescriptor(int binding, String type, String name, int imageIdx, int descriptorType) {
      this.binding = binding;
      this.qualifier = type;
      this.name = name;
      this.imageIdx = imageIdx;
      if (this.imageIdx == -1) {
         throw new IllegalArgumentException();
      }

      this.descriptorType = descriptorType;
      boolean isStorageImage = this.isStorageImage();
      this.useSampler = !isStorageImage;
      this.setLayout(isStorageImage ? 1 : 5);
   }

   @Override
   public int getBinding() {
      return this.binding;
   }

   @Override
   public int getType() {
      return this.descriptorType;
   }

   @Override
   public int getStages() {
      return 63;
   }

   public void setLayout(int layout) {
      this.layout = layout;
      this.isReadOnlyLayout = layout == 5;
   }

   public int getLayout() {
      return this.layout;
   }

   public void setMipLevel(int mipLevel) {
      this.mipLevel = mipLevel;
   }

   public int getMipLevel() {
      return this.mipLevel;
   }

   public VulkanImage getImage() {
      return VTextureSelector.getImage(this.imageIdx);
   }

   public long getImageView(VulkanImage image) {
      long view;
      if (this.mipLevel == -1) {
         view = image.getImageView();
      } else {
         view = image.getLevelImageView(this.mipLevel);
      }

      return view;
   }

   public boolean isStorageImage() {
      return this.descriptorType == 3;
   }

   public static class State {
      long imageView;
      long sampler;

      public State(long imageView, long sampler) {
         this.set(imageView, sampler);
      }

      public void set(long imageView, long sampler) {
         this.imageView = imageView;
         this.sampler = sampler;
      }

      public boolean isCurrentState(long imageView, long sampler) {
         return this.imageView == imageView && this.sampler == sampler;
      }
   }
}
