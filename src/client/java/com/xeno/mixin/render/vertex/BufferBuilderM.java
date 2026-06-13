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
package com.xeno.mixin.render.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.xeno.interfaces.ExtendedVertexBuilder;
import com.xeno.render.vertex.format.I32_SNorm;
import com.xeno.vulkan.util.MemoryAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderM implements VertexConsumer, ExtendedVertexBuilder {
   @Shadow
   private boolean fastFormat;
   @Shadow
   private boolean fullFormat;
   @Shadow
   private VertexFormat format;
   @Shadow
   private int elementsToFill;
   @Shadow
   @Final
   private int initialElementsToFill;
   private long ptr;
   private final float[] brightness = new float[4];
   private final int[] lights = new int[4];

   @Shadow
   protected abstract long beginVertex();

   @Shadow
   protected abstract long beginElement(VertexFormatElement var1);

   @Override
   public void vertex(float x, float y, float z, int packedColor, float u, float v, int overlay, int light, int packedNormal) {
      this.ptr = this.beginVertex();
      if (this.format == DefaultVertexFormat.ENTITY) {
         MemoryAccess.memPutFloat(this.ptr + 0L, x);
         MemoryAccess.memPutFloat(this.ptr + 4L, y);
         MemoryAccess.memPutFloat(this.ptr + 8L, z);
         MemoryAccess.memPutInt(this.ptr + 12L, packedColor);
         MemoryAccess.memPutFloat(this.ptr + 16L, u);
         MemoryAccess.memPutFloat(this.ptr + 20L, v);
         MemoryAccess.memPutInt(this.ptr + 24L, overlay);
         MemoryAccess.memPutInt(this.ptr + 28L, light);
         MemoryAccess.memPutInt(this.ptr + 32L, packedNormal);
      } else {
         this.elementsToFill = this.initialElementsToFill;
         this.position(x, y, z);
         this.fastColor(packedColor);
         this.fastUv(u, v);
         this.fastOverlay(overlay);
         this.light(light);
         this.fastNormal(packedNormal);
      }
   }

   @Override
   public void vertex(float x, float y, float z, float u, float v, int packedColor, int light) {
      this.ptr = this.beginVertex();
      MemoryAccess.memPutFloat(this.ptr + 0L, x);
      MemoryAccess.memPutFloat(this.ptr + 4L, y);
      MemoryAccess.memPutFloat(this.ptr + 8L, z);
      MemoryAccess.memPutFloat(this.ptr + 12L, u);
      MemoryAccess.memPutFloat(this.ptr + 16L, v);
      MemoryAccess.memPutInt(this.ptr + 20L, packedColor);
      MemoryAccess.memPutInt(this.ptr + 24L, light);
   }

   public void position(float x, float y, float z) {
      MemoryAccess.memPutFloat(this.ptr + 0L, x);
      MemoryAccess.memPutFloat(this.ptr + 4L, y);
      MemoryAccess.memPutFloat(this.ptr + 8L, z);
   }

   public void fastColor(int packedColor) {
      long ptr = this.beginElement(VertexFormatElement.COLOR);
      if (ptr != -1L) {
         MemoryAccess.memPutInt(ptr, packedColor);
      }
   }

   public void fastUv(float u, float v) {
      long ptr = this.beginElement(VertexFormatElement.UV0);
      if (ptr != -1L) {
         MemoryAccess.memPutFloat(ptr, u);
         MemoryAccess.memPutFloat(ptr + 4L, v);
      }
   }

   public void fastOverlay(int o) {
      long ptr = this.beginElement(VertexFormatElement.UV1);
      if (ptr != -1L) {
         MemoryAccess.memPutInt(ptr, o);
      }
   }

   public void light(int l) {
      long ptr = this.beginElement(VertexFormatElement.UV2);
      if (ptr != -1L) {
         MemoryAccess.memPutInt(ptr, l);
      }
   }

   public void fastNormal(int packedNormal) {
      long ptr = this.beginElement(VertexFormatElement.NORMAL);
      if (ptr != -1L) {
         MemoryAccess.memPutInt(ptr, packedNormal);
      }
   }

   @Overwrite
   @Override
   public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
      if (this.fastFormat) {
         long ptr = this.beginVertex();
         MemoryAccess.memPutFloat(ptr + 0L, x);
         MemoryAccess.memPutFloat(ptr + 4L, y);
         MemoryAccess.memPutFloat(ptr + 8L, z);
         MemoryAccess.memPutInt(ptr + 12L, color);
         MemoryAccess.memPutFloat(ptr + 16L, u);
         MemoryAccess.memPutFloat(ptr + 20L, v);
         byte i;
         if (this.fullFormat) {
            MemoryAccess.memPutInt(ptr + 24L, overlay);
            i = 28;
         } else {
            i = 24;
         }

         MemoryAccess.memPutInt(ptr + i, light);
         int temp = I32_SNorm.packNormal(normalX, normalY, normalZ);
         MemoryAccess.memPutInt(ptr + i + 4L, temp);
      } else {
         VertexConsumer.super.addVertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
      }
   }

   @Overwrite
   public VertexConsumer setColor(int r, int g, int b, int a) {
      long m = this.beginElement(VertexFormatElement.COLOR);
      if (m != -1L) {
         int color = packRgba(r, g, b, a);
         MemoryAccess.memPutInt(m, color);
      }

      return this;
   }

   private static int packRgba(int r, int g, int b, int a) {
      return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | r & 0xFF;
   }
}
