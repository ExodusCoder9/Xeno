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
package com.xeno.vulkan.texture;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.nio.LongBuffer;
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.device.DeviceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSamplerReductionModeCreateInfo;

public abstract class SamplerManager {
   public static final int ADDRESS_MODE_BITS = 2;
   public static final int REDUCTION_MODE_BITS = 2;
   public static final int COMPARE_OP_BITS = 3;
   public static final int ADDRESS_MODE_U_OFFSET = 0;
   public static final int ADDRESS_MODE_V_OFFSET = 2;
   public static final int MIN_FILTER_OFFSET = 4;
   public static final int MAG_FILTER_OFFSET = 5;
   public static final int MIPMAP_MODE_OFFSET = 6;
   public static final int ANISOTROPY_OFFSET = 7;
   public static final int COMPARE_ENABLED_OFFSET = 8;
   public static final int COMPARE_OP_OFFSET = 9;
   public static final int REDUCTION_MODE_ENABLE_OFFSET = 12;
   public static final int REDUCTION_MODE_OFFSET = 13;
   static final float MIP_BIAS = -0.5F;
   static final Object2LongMap<SamplerInfo> SAMPLERS = new Object2LongOpenHashMap();

   public static long getSampler(boolean clamp, boolean linearFiltering, int maxLod) {
      return getSampler(clamp, linearFiltering, maxLod, false, 0);
   }

   public static long getSampler(boolean clamp, boolean linearFiltering, int maxLod, boolean anisotropy, int maxAnisotropy) {
      int addressMode = clamp ? 2 : 0;
      int filter = linearFiltering ? 1 : 0;
      int mipmapMode = linearFiltering ? 1 : 0;
      return getSampler(addressMode, addressMode, filter, filter, mipmapMode, maxLod, anisotropy, maxAnisotropy, -1);
   }

   public static long getSampler(
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
      SamplerInfo samplerInfo = new SamplerInfo(addressModeU, addressModeV, minFilter, magFilter, mipmapMode, maxLod, anisotropy, maxAnisotropy, reductionMode);
      return getSampler(samplerInfo);
   }

   public static long getSampler(SamplerInfo samplerInfo) {
      long sampler = SAMPLERS.getOrDefault(samplerInfo, 0L);
      if (sampler == 0L) {
         sampler = createTextureSampler(samplerInfo);
         SAMPLERS.put(samplerInfo, sampler);
      }

      return sampler;
   }

   public static long getDefaultSampler() {
      return getSampler(false, false, 0);
   }

   public static long createTextureSampler(SamplerInfo sampler) {
      int state = sampler.encodedState;
      MemoryStack stack = MemoryStack.stackPush();

      long var5;
      try {
         VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack);
         samplerInfo.sType(31);
         samplerInfo.magFilter(sampler.getMagFilter());
         samplerInfo.minFilter(sampler.getMinFilter());
         samplerInfo.addressModeU(sampler.getAddressModeU());
         samplerInfo.addressModeV(sampler.getAddressModeV());
         samplerInfo.addressModeW(0);
         samplerInfo.anisotropyEnable(sampler.getAnisotropy());
         samplerInfo.maxAnisotropy(sampler.getMaxAnisotropy());
         samplerInfo.borderColor(5);
         samplerInfo.unnormalizedCoordinates(false);
         samplerInfo.compareEnable(sampler.compareEnabled());
         samplerInfo.compareOp(sampler.getCompareOp());
         samplerInfo.mipmapMode(sampler.getMipmapMode());
         samplerInfo.maxLod(sampler.getMaxLod());
         samplerInfo.minLod(0.0F);
         samplerInfo.mipLodBias(0.0F);
         if (sampler.hasReductionMode()) {
            VkSamplerReductionModeCreateInfo reductionModeInfo = VkSamplerReductionModeCreateInfo.calloc(stack);
            reductionModeInfo.sType$Default();
            reductionModeInfo.reductionMode(sampler.getReductionMode());
            samplerInfo.pNext(reductionModeInfo.address());
         }

         LongBuffer pTextureSampler = stack.mallocLong(1);
         if (VK10.vkCreateSampler(Vulkan.getVkDevice(), samplerInfo, null, pTextureSampler) != 0) {
            throw new RuntimeException("Failed to create texture sampler");
         }

         var5 = pTextureSampler.get(0);
      } catch (Throwable var8) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (stack != null) {
         stack.close();
      }

      return var5;
   }

   public static void cleanUp() {
      LongIterator var0 = SAMPLERS.values().iterator();

      while (var0.hasNext()) {
         long id = (Long)var0.next();
         VK10.vkDestroySampler(DeviceManager.vkDevice, id, null);
      }
   }

   static int getEncodedState(
      int addressModeU, int addressModeV, int minFilter, int magFilter, int mipmapMode, boolean anisotropy, boolean compare, int compareOp, int reductionMode
   ) {
      int encodedState = (addressModeU & 2) << 0;
      encodedState |= (addressModeV & 2) << 2;
      encodedState |= (minFilter & 1) << 4;
      encodedState |= (magFilter & 1) << 5;
      encodedState |= (mipmapMode & 1) << 6;
      encodedState |= ((anisotropy ? 1 : 0) & 1) << 7;
      encodedState |= ((compare ? 1 : 0) & 1) << 8;
      encodedState |= (compareOp & 3) << 9;
      encodedState |= (reductionMode != -1 ? 1 : 0) << 12;
      return encodedState | (reductionMode & 2) << 13;
   }
}
