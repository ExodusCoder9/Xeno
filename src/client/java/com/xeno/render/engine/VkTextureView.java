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

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.Nullable;

public class VkTextureView extends GpuTextureView {
   private boolean closed;
   private final Int2ReferenceMap<VkFbo> fboCache = new Int2ReferenceOpenHashMap();

   protected VkTextureView(VkGpuTexture gpuTexture, int baseMipLevel, int mipLevels) {
      super(gpuTexture, baseMipLevel, mipLevels);
      gpuTexture.addViews();
   }

   public VkFbo getFbo(@Nullable GpuTexture depthAttachment) {
      int depthAttachmentId = depthAttachment == null ? 0 : ((VkGpuTexture)depthAttachment).id;
      return (VkFbo)this.fboCache.computeIfAbsent(depthAttachmentId, j -> new VkFbo(this, (VkGpuTexture)depthAttachment));
   }

   @Override
   public boolean isClosed() {
      return this.closed;
   }

   @Override
   public void close() {
      if (!this.closed) {
         this.closed = true;
         this.texture().removeViews();
      }

      ObjectIterator var1 = this.fboCache.values().iterator();

      while (var1.hasNext()) {
         VkFbo fbo = (VkFbo)var1.next();
         fbo.close();
      }
   }

   public VkGpuTexture texture() {
      return (VkGpuTexture)super.texture();
   }
}
