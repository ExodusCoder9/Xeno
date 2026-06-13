package com.xeno.render.engine;

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


import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.xeno.gl.VkGlTexture;
import com.xeno.vulkan.texture.VulkanImage;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VkGpuTexture extends GlTexture {
   private static final Reference2ReferenceOpenHashMap<GlTexture, VkGpuTexture> glToVkMap = new Reference2ReferenceOpenHashMap();
   protected VkGlTexture glTexture;
   protected final int id;
   private final Int2ReferenceMap<VkFbo> fboCache = new Int2ReferenceOpenHashMap();
   protected boolean closed;
   VkTextureView fboView;
   boolean needsClear = false;
   int clearColor = 0;
   float depthClearValue = 1.0F;

   protected VkGpuTexture(int usage, String string, TextureFormat textureFormat, int width, int height, int layers, int mipLevel, int id, VkGlTexture glTexture) {
      super(usage, string, textureFormat, width, height, layers, mipLevel, id);
      this.id = id;
      this.glTexture = glTexture;
   }

   public void close() {
      if (!this.closed) {
         this.closed = true;
         GlStateManager._deleteTexture(this.id);
      }
   }

   public boolean isClosed() {
      return this.closed;
   }

   public int glId() {
      return this.id;
   }

   public void setClearColor(int clearColor) {
      this.needsClear = true;
      this.clearColor = clearColor;
   }

   public void setDepthClearValue(float depthClearValue) {
      this.needsClear = true;
      this.depthClearValue = depthClearValue;
   }

   public boolean needsClear() {
      return this.needsClear;
   }

   public VkFbo getFbo(@Nullable GpuTexture depthAttachment) {
      int depthAttachmentId = depthAttachment == null ? 0 : ((VkGpuTexture)depthAttachment).id;
      if (this.fboView == null) {
         VkGpuDevice gpuDevice = (VkGpuDevice)RenderSystem.getDevice().backend;
         this.fboView = (VkTextureView)gpuDevice.createTextureView(this, 0, this.getMipLevels());
      }

      return (VkFbo)this.fboCache.computeIfAbsent(depthAttachmentId, j -> new VkFbo(this.fboView, (VkGpuTexture)depthAttachment));
   }

   public VulkanImage getVulkanImage() {
      return this.glTexture.getVulkanImage();
   }

   public static VkGpuTexture fromGlTexture(GlTexture glTexture) {
      return (VkGpuTexture)glToVkMap.computeIfAbsent(
         glTexture,
         glTexture1 -> {
            String name = glTexture.getLabel();
            int id = glTexture.glId();
            VkGlTexture vglTexture = VkGlTexture.getTexture(id);
            return new VkGpuTexture(
               0, name, glTexture.getFormat(), glTexture.getWidth(0), glTexture.getHeight(0), 1, glTexture.getMipLevels(), glTexture.glId(), vglTexture
            );
         }
      );
   }

   public static TextureFormat textureFormat(int format) {
      return switch (format) {
         case 9 -> TextureFormat.RED8;
         case 37, 44 -> TextureFormat.RGBA8;
         case 125, 126, 129 -> TextureFormat.DEPTH32;
         default -> null;
      };
   }

   public static int vkFormat(TextureFormat textureFormat) {
      return switch (textureFormat) {
         case RGBA8 -> 37;
         case RED8 -> 9;
         case RED8I -> 14;
         case DEPTH32 -> 126;
         default -> throw new MatchException(null, null);
      };
   }

   public static int vkImageViewType(int usage) {
      int viewType;
      if ((usage & 16) != 0) {
         viewType = 3;
      } else {
         viewType = 1;
      }

      return viewType;
   }
}
