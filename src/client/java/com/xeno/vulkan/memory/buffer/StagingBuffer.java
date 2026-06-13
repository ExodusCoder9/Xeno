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
package com.xeno.vulkan.memory.buffer;

import java.nio.ByteBuffer;
import com.xeno.render.chunk.buffer.UploadManager;
import com.xeno.render.chunk.util.Util;
import com.xeno.render.texture.ImageUploadHelper;
import com.xeno.vulkan.Renderer;
import com.xeno.vulkan.memory.MemoryTypes;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCString;

public class StagingBuffer extends Buffer {
   private static final long DEFAULT_SIZE = 67108864L;
   private StagingBuffers parent;

   public StagingBuffer() {
      this(67108864L);
   }

   public StagingBuffer(long size) {
      super("Staging buffer", 1, MemoryTypes.HOST_MEM);
      this.createBuffer(size);
   }

   public void setParent(StagingBuffers parent) {
      this.parent = parent;
   }

   public void copyBuffer(int size, ByteBuffer byteBuffer) {
      this.copyBuffer(size, MemoryUtil.memAddress(byteBuffer));
   }

   public void copyBuffer(int size, long scrPtr) {
      if (size > this.bufferSize) {
         throw new IllegalArgumentException("Upload size is greater than staging buffer size.");
      }

      if (size > this.bufferSize - this.usedBytes) {
         if (this.parent != null) {
            this.parent.submitCurrentBuffer();
         } else {
            this.submitUploads();
         }
      }

      LibCString.nmemcpy(this.dataPtr + this.usedBytes, scrPtr, size);
      this.offset = this.usedBytes;
      this.usedBytes += size;
   }

   public void align(int alignment) {
      long alignedOffset = Util.align(this.usedBytes, alignment);
      if (alignedOffset > this.bufferSize) {
         if (this.parent != null) {
            this.parent.submitCurrentBuffer();
         } else {
            this.submitUploads();
         }
         alignedOffset = 0L;
      }

      this.usedBytes = alignedOffset;
   }

   public void submitUploads() {
      UploadManager.INSTANCE.submitUploads();
      ImageUploadHelper.INSTANCE.submitCommands(false);
      Renderer.getInstance().flushCmds();
      this.resizeBuffer(this.bufferSize);
      this.reset();
   }
}
