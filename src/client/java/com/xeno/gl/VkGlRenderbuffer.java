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
package com.xeno.gl;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.nio.ByteBuffer;
import com.xeno.vulkan.texture.ImageUtil;
import com.xeno.vulkan.texture.SamplerManager;
import com.xeno.vulkan.texture.VTextureSelector;
import com.xeno.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryUtil;

public class VkGlRenderbuffer {
   private static int ID_COUNTER = 1;
   private static final Int2ReferenceOpenHashMap<VkGlRenderbuffer> map = new Int2ReferenceOpenHashMap();
   private static int boundId = 0;
   private static VkGlRenderbuffer bound;
   final int id;
   VulkanImage vulkanImage;
   int internalFormat;
   boolean needsUpdate = false;
   int maxLevel = 0;
   int maxLod = 0;
   int minFilter;
   int magFilter = 9729;

   public static int genId() {
      int id = ID_COUNTER;
      map.put(id, new VkGlRenderbuffer(id));
      ID_COUNTER++;
      return id;
   }

   public static void bindRenderbuffer(int target, int id) {
      boundId = id;
      bound = (VkGlRenderbuffer)map.get(id);
      if (id > 0) {
         if (bound == null) {
            throw new NullPointerException("bound texture is null");
         }

         VulkanImage vulkanImage = bound.vulkanImage;
         if (vulkanImage != null) {
            VTextureSelector.bindTexture(vulkanImage);
         }
      }
   }

   public static void deleteRenderbuffer(int i) {
      map.remove(i);
   }

   public static VkGlRenderbuffer getRenderbuffer(int id) {
      return (VkGlRenderbuffer)map.get(id);
   }

   public static void renderbufferStorage(int target, int internalFormat, int width, int height) {
      if (width != 0 && height != 0) {
         bound.internalFormat = internalFormat;
         bound.allocateIfNeeded(width, height, internalFormat);
      }
   }

   public static void texParameteri(int target, int pName, int param) {
      if (target != 3553) {
         throw new UnsupportedOperationException();
      }

      switch (pName) {
         case 10240:
            bound.setMagFilter(param);
            break;
         case 10241:
            bound.setMinFilter(param);
         case 33082:
         case 34049:
         default:
            break;
         case 33083:
            bound.setMaxLod(param);
            break;
         case 33085:
            bound.setMaxLevel(param);
      }
   }

   public static int getTexLevelParameter(int target, int level, int pName) {
      if (bound != null && target != 3553) {
         return switch (pName) {
            case 4096 -> bound.vulkanImage.width;
            case 4097 -> bound.vulkanImage.height;
            default -> -1;
            case 4099 -> GlUtil.getGlFormat(bound.vulkanImage.format);
         };
      } else {
         return -1;
      }
   }

   public static void generateMipmap(int target) {
      if (target != 3553) {
         throw new UnsupportedOperationException();
      }

      bound.generateMipmaps();
   }

   public static void setVulkanImage(int id, VulkanImage vulkanImage) {
      VkGlRenderbuffer texture = (VkGlRenderbuffer)map.get(id);
      texture.vulkanImage = vulkanImage;
   }

   public static VkGlRenderbuffer getBound() {
      return bound;
   }

   public VkGlRenderbuffer(int id) {
      this.id = id;
   }

   void allocateIfNeeded(int width, int height, int format) {
      int vkFormat = GlUtil.vulkanFormat(format);
      this.needsUpdate = this.needsUpdate
         | (this.vulkanImage == null || this.vulkanImage.width != width || this.vulkanImage.height != height || vkFormat != this.vulkanImage.format);
      if (this.needsUpdate) {
         this.allocateImage(width, height, vkFormat);
         this.updateSampler();
         this.needsUpdate = false;
      }
   }

   void allocateImage(int width, int height, int vkFormat) {
      if (this.vulkanImage != null) {
         this.vulkanImage.free();
      }

      if (VulkanImage.isDepthFormat(vkFormat)) {
         this.vulkanImage = VulkanImage.createDepthImage(vkFormat, width, height, 37, false, true);
      } else {
         this.vulkanImage = new VulkanImage.Builder(width, height).setMipLevels(this.maxLevel + 1).setFormat(vkFormat).addUsage(16).createVulkanImage();
      }

      VTextureSelector.bindTexture(this.vulkanImage);
   }

   void updateSampler() {
      if (this.vulkanImage != null) {
         int addressMode = 2;
         int vkMinFilter;
         int mipmapMode;
         switch (this.minFilter) {
            case 9728:
            case 9984:
               vkMinFilter = 0;
               mipmapMode = 0;
               break;
            case 9729:
            case 9987:
               vkMinFilter = 1;
               mipmapMode = 1;
               break;
            case 9985:
               vkMinFilter = 1;
               mipmapMode = 0;
               break;
            case 9986:
               vkMinFilter = 0;
               mipmapMode = 1;
               break;
            default:
               throw new IllegalStateException("Unexpected min filter value: %d".formatted(this.minFilter));
         }
         int vkMagFilter = switch (this.magFilter) {
            case 9728 -> 0;
            case 9729 -> 1;
            default -> throw new IllegalStateException("Unexpected mag filter value: %d".formatted(this.magFilter));
         };
         long sampler = SamplerManager.getSampler(addressMode, addressMode, vkMinFilter, vkMagFilter, mipmapMode, this.maxLod, false, 0.0F, -1);
         this.vulkanImage.setSampler(sampler);
      }
   }

   private void uploadImage(ByteBuffer pixels) {
      int width = this.vulkanImage.width;
      int height = this.vulkanImage.height;
      if (this.internalFormat == 6407 && this.vulkanImage.format == 37) {
         ByteBuffer RGBA_buffer = GlUtil.RGBtoRGBA_buffer(pixels);
         this.vulkanImage.uploadSubTextureAsync(0, width, height, 0, 0, 0, 0, 0, RGBA_buffer);
         MemoryUtil.memFree(RGBA_buffer);
      } else {
         this.vulkanImage.uploadSubTextureAsync(0, width, height, 0, 0, 0, 0, 0, pixels);
      }
   }

   void generateMipmaps() {
      ImageUtil.generateMipmaps(this.vulkanImage);
   }

   void setMaxLevel(int l) {
      if (l < 0) {
         throw new IllegalStateException("max level cannot be < 0.");
      }

      if (this.maxLevel != l) {
         this.maxLevel = l;
         this.needsUpdate = true;
      }
   }

   void setMaxLod(int l) {
      if (l < 0) {
         throw new IllegalStateException("max level cannot be < 0.");
      }

      if (this.maxLod != l) {
         this.maxLod = l;
         this.updateSampler();
      }
   }

   void setMagFilter(int v) {
      switch (v) {
         case 9728:
         case 9729:
            this.magFilter = v;
            this.updateSampler();
            return;
         default:
            throw new IllegalArgumentException("illegal mag filter value: " + v);
      }
   }

   void setMinFilter(int v) {
      switch (v) {
         case 9728:
         case 9729:
         case 9984:
         case 9985:
         case 9986:
         case 9987:
            this.minFilter = v;
            this.updateSampler();
            return;
         default:
            throw new IllegalArgumentException("illegal min filter value: " + v);
      }
   }

   public VulkanImage getVulkanImage() {
      return this.vulkanImage;
   }

   public void setVulkanImage(VulkanImage vulkanImage) {
      this.vulkanImage = vulkanImage;
   }
}
