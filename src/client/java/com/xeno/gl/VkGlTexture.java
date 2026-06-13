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
import java.nio.IntBuffer;
import com.xeno.Initializer;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.texture.ImageUtil;
import com.xeno.vulkan.texture.SamplerManager;
import com.xeno.vulkan.texture.VTextureSelector;
import com.xeno.vulkan.texture.VulkanImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public class VkGlTexture {
   private static int ID_COUNTER = 1;
   private static final Int2ReferenceOpenHashMap<VkGlTexture> map = new Int2ReferenceOpenHashMap();
   private static int boundTextureId = 0;
   private static VkGlTexture boundTexture;
   private static int activeTexture = 0;
   private static int unpackRowLength;
   private static int unpackSkipRows;
   private static int unpackSkipPixels;
   public final int id;
   VulkanImage vulkanImage;
   int width;
   int height;
   int vkFormat;
   boolean needsUpdate = false;
   int maxLevel = 0;
   int maxLod = 0;
   int minFilter = 9728;
   int magFilter = 9728;
   boolean clamp = true;

   public static void bindIdToImage(int id, VulkanImage vulkanImage) {
      VkGlTexture texture = (VkGlTexture)map.get(id);
      texture.vulkanImage = vulkanImage;
   }

   public static int genTextureId() {
      int id = ID_COUNTER;
      map.put(id, new VkGlTexture(id));
      ID_COUNTER++;
      return id;
   }

   public static void bindTexture(int id) {
      boundTextureId = id;
      boundTexture = (VkGlTexture)map.get(id);
      if (id > 0) {
         if (boundTexture == null) {
            Initializer.LOGGER.error("Invalid id({}) value", id);
         } else {
            VulkanImage vulkanImage = boundTexture.vulkanImage;
            if (vulkanImage != null) {
               VTextureSelector.bindTexture(activeTexture, vulkanImage);
            }
         }
      }
   }

   public static void glDeleteTextures(IntBuffer intBuffer) {
      for (int i = intBuffer.position(); i < intBuffer.limit(); i++) {
         glDeleteTextures(intBuffer.get(i));
      }
   }

   public static void glDeleteTextures(int i) {
      VkGlTexture glTexture = (VkGlTexture)map.remove(i);
      VulkanImage image = glTexture != null ? glTexture.vulkanImage : null;
      if (image != null) {
         MemoryManager.getInstance().addToFreeable(image);
      }
   }

   public static VkGlTexture getTexture(int id) {
      return id == 0 ? null : (VkGlTexture)map.get(id);
   }

   public static void activeTexture(int i) {
      activeTexture = i - 33984;
      VTextureSelector.setActiveTexture(activeTexture);
   }

   public static void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixels) {
      if (!checkParams(level, width, height)) {
         boundTexture.updateParams(level, width, height, internalFormat, type);
         boundTexture.allocateIfNeeded();
         VTextureSelector.bindTexture(activeTexture, boundTexture.vulkanImage);
         texSubImage2D(target, level, 0, 0, width, height, format, type, pixels);
      }
   }

   public static void texImage2D(
      int target, int level, int internalFormat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels
   ) {
      if (!checkParams(level, width, height)) {
         boundTexture.updateParams(level, width, height, internalFormat, type);
         boundTexture.allocateIfNeeded();
         VTextureSelector.bindTexture(activeTexture, boundTexture.vulkanImage);
         texSubImage2D(target, level, 0, 0, width, height, format, type, pixels);
      }
   }

   private static boolean checkParams(int level, int width, int height) {
      return width == 0 || height == 0;
   }

   public static void texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, long pixels) {
      if (width != 0 && height != 0) {
         VkGlBuffer glBuffer = VkGlBuffer.getPixelUnpackBufferBound();
         ByteBuffer src;
         if (glBuffer != null) {
            glBuffer.data.position((int)pixels);
            src = glBuffer.data;
         } else if (pixels != 0L) {
            src = getByteBuffer(width, height, pixels);
         } else {
            src = null;
         }

         if (src != null) {
            boundTexture.uploadSubImage(level, xOffset, yOffset, width, height, format, src);
         }
      }
   }

   private static ByteBuffer getByteBuffer(int width, int height, long pixels) {
      int formatSize = 4;
      int rowLength = unpackRowLength != 0 ? unpackRowLength : width;
      int offset = (unpackSkipRows * rowLength + unpackSkipPixels) * formatSize;
      return MemoryUtil.memByteBuffer(pixels + offset, (rowLength * height - unpackSkipPixels) * formatSize);
   }

   public static void texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, @Nullable ByteBuffer pixels) {
      if (width != 0 && height != 0) {
         VkGlBuffer glBuffer = VkGlBuffer.getPixelUnpackBufferBound();
         ByteBuffer src;
         if (glBuffer != null) {
            if (pixels != null) {
               throw new IllegalStateException("Trying to use pixel buffer when there is a Pixel Unpack Buffer bound.");
            }

            glBuffer.data.position(0);
            src = glBuffer.data;
         } else {
            src = pixels;
         }

         if (src != null) {
            boundTexture.uploadSubImage(level, xOffset, yOffset, width, height, format, src);
         }
      }
   }

   public static void texParameteri(int target, int pName, int param) {
      if (target != 3553) {
         throw new UnsupportedOperationException("target != GL_TEXTURE_2D not supported");
      }

      if (boundTexture != null) {
         switch (pName) {
            case 10240:
               boundTexture.setMagFilter(param);
               break;
            case 10241:
               boundTexture.setMinFilter(param);
               break;
            case 10242:
            case 10243:
               boundTexture.setClamp(param);
            case 33082:
            case 34049:
            default:
               break;
            case 33083:
               boundTexture.setMaxLod(param);
               break;
            case 33085:
               boundTexture.setMaxLevel(param);
         }
      }
   }

   public static int getTexParameteri(int target, int pName) {
      if (target != 3553) {
         throw new UnsupportedOperationException("target != GL_TEXTURE_2D not supported");
      }

      if (boundTexture == null) {
         return -1;
      }

      return switch (pName) {
         case 4096 -> boundTexture.vulkanImage.width;
         case 4097 -> boundTexture.vulkanImage.height;
         case 4099 -> GlUtil.getGlFormat(boundTexture.vulkanImage.format);
         case 10240 -> boundTexture.magFilter;
         case 10241 -> boundTexture.minFilter;
         case 33083 -> boundTexture.maxLod;
         case 33085 -> boundTexture.maxLevel;
         default -> -1;
      };
   }

   public static int getTexLevelParameter(int target, int level, int pName) {
      if (target != 3553) {
         throw new UnsupportedOperationException("target != GL_TEXTURE_2D not supported");
      }

      if (boundTexture == null) {
         return -1;
      }

      return switch (pName) {
         case 4096 -> boundTexture.vulkanImage.width;
         case 4097 -> boundTexture.vulkanImage.height;
         default -> -1;
         case 4099 -> GlUtil.getGlFormat(boundTexture.vulkanImage.format);
      };
   }

   public static void pixelStoreI(int pName, int value) {
      switch (pName) {
         case 3314:
            unpackRowLength = value;
            break;
         case 3315:
            unpackSkipRows = value;
            break;
         case 3316:
            unpackSkipPixels = value;
      }
   }

   public static void generateMipmap(int target) {
      if (target != 3553) {
         throw new UnsupportedOperationException("target != GL_TEXTURE_2D not supported");
      }
   }

   public static void getTexImage(int tex, int level, int format, int type, long pixels) {
      VulkanImage image = boundTexture.vulkanImage;
      VkGlBuffer buffer = VkGlBuffer.getPixelPackBufferBound();
      long ptr;
      if (buffer != null) {
         buffer.data.position((int)pixels);
         ptr = MemoryUtil.memAddress(buffer.data);
      } else {
         ptr = pixels;
      }

      ImageUtil.downloadTexture(image, ptr);
   }

   public static void setVulkanImage(int id, VulkanImage vulkanImage) {
      VkGlTexture texture = (VkGlTexture)map.get(id);
      texture.vulkanImage = vulkanImage;
   }

   public static VkGlTexture getBoundTexture() {
      return boundTexture;
   }

   public VkGlTexture(int id) {
      this.id = id;
   }

   void updateParams(int level, int width, int height, int internalFormat, int type) {
      if (level > this.maxLevel) {
         this.maxLevel = level;
         this.needsUpdate = true;
      }

      if (level == 0) {
         int vkFormat = GlUtil.vulkanFormat(internalFormat, type);
         if (this.vulkanImage == null || this.width != width || this.height != height || vkFormat != this.vulkanImage.format) {
            this.width = width;
            this.height = height;
            this.vkFormat = vkFormat;
            this.needsUpdate = true;
         }
      }
   }

   void allocateIfNeeded() {
      if (this.needsUpdate) {
         this.allocateImage(this.width, this.height, this.vkFormat);
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
         this.vulkanImage = new VulkanImage.Builder(width, height)
            .setName(String.format("GlTexture %d", this.id))
            .setMipLevels(this.maxLevel + 1)
            .setFormat(vkFormat)
            .addUsage(16)
            .createVulkanImage();
      }
   }

   void updateSampler() {
      if (this.vulkanImage != null) {
         int addressMode = this.clamp ? 2 : 0;
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

   private void uploadSubImage(int level, int xOffset, int yOffset, int width, int height, int format, ByteBuffer pixels) {
      ByteBuffer src;
      if (format == 6407 && this.vulkanImage.format == 37) {
         src = GlUtil.RGBtoRGBA_buffer(pixels);
      } else if (format == 32993 && this.vulkanImage.format == 37) {
         src = GlUtil.BGRAtoRGBA_buffer(pixels);
      } else {
         src = pixels;
      }

      this.vulkanImage.uploadSubTextureAsync(level, width, height, xOffset, yOffset, 0, 0, unpackRowLength, src);
      if (src != pixels) {
         MemoryUtil.memFree(src);
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

   void setClamp(int v) {
      if (v == 33071) {
         this.clamp = true;
      } else {
         this.clamp = false;
      }

      this.updateSampler();
   }

   public VulkanImage getVulkanImage() {
      return this.vulkanImage;
   }

   public void setVulkanImage(VulkanImage vulkanImage) {
      this.vulkanImage = vulkanImage;
      this.width = vulkanImage.width;
      this.height = vulkanImage.height;
      this.maxLevel = vulkanImage.mipLevels;
      this.vkFormat = vulkanImage.format;
   }
}
