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
package com.xeno.mixin.render.buffer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import com.xeno.render.engine.VkGpuBuffer;
import com.xeno.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(VertexFormat.class)
public class VertexFormatMixin {
   @Overwrite
   public GpuBuffer uploadImmediateVertexBuffer(ByteBuffer byteBuffer) {
      VkGpuBuffer buffer = Renderer.getDrawer().getGpuBuffers().getVertexBuffer();
      buffer.getBuffer().copyBuffer(byteBuffer, byteBuffer.remaining());
      int currentOffset = Math.toIntExact(buffer.getBuffer().getOffset());
      buffer.setOffset(currentOffset);
      return buffer;
   }

   @Overwrite
   public GpuBuffer uploadImmediateIndexBuffer(ByteBuffer byteBuffer) {
      VkGpuBuffer buffer = Renderer.getDrawer().getGpuBuffers().getIndexBuffer();
      buffer.getBuffer().copyBuffer(byteBuffer, byteBuffer.remaining());
      int currentOffset = Math.toIntExact(buffer.getBuffer().getOffset());
      buffer.setOffset(currentOffset);
      return buffer;
   }
}
