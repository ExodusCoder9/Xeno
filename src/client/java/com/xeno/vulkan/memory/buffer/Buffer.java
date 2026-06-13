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
import com.xeno.vulkan.Vulkan;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.memory.MemoryType;
import org.lwjgl.system.MemoryStack;

public class Buffer {
   public final String name;
   public final MemoryType type;
   public final int usage;
   protected long id;
   protected long allocation;
   protected long bufferSize;
   protected long usedBytes;
   protected long offset;
   protected long dataPtr;

   public Buffer(String name, int usage, MemoryType type) {
      this.name = name;
      this.usage = usage;
      this.type = type;
   }

   public void createBuffer(long bufferSize) {
      this.type.createBuffer(this, bufferSize);
      if (this.name != null) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            Vulkan.setDebugLabel(stack, 9, this.id, this.name);
         } catch (Throwable var7) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (stack != null) {
            stack.close();
         }
      }

      if (this.type.mappable()) {
         this.dataPtr = MemoryManager.getInstance().Map(this.allocation).get(0);
      }
   }

   public void resizeBuffer(long newSize) {
      MemoryManager.getInstance().addToFreeable(this);
      this.createBuffer(newSize);
   }

   public void copyBuffer(ByteBuffer byteBuffer, int size) {
      if (size > this.bufferSize - this.usedBytes) {
         this.resizeBuffer((this.bufferSize + size) * 2L);
      }

      this.type.copyToBuffer(this, byteBuffer, size, 0L, this.usedBytes);
      this.offset = this.usedBytes;
      this.usedBytes += size;
   }

   public void copyBuffer(ByteBuffer byteBuffer, int size, int dstOffset) {
      if (size > this.bufferSize - dstOffset) {
         this.resizeBuffer((this.bufferSize + size) * 2L);
      }

      this.type.copyToBuffer(this, byteBuffer, size, 0L, dstOffset);
      this.offset = dstOffset;
      this.usedBytes = dstOffset + size;
   }

   public void scheduleFree() {
      MemoryManager.getInstance().addToFreeable(this);
   }

   public void reset() {
      this.usedBytes = 0L;
   }

   public long getAllocation() {
      return this.allocation;
   }

   public long getUsedBytes() {
      return this.usedBytes;
   }

   public long getOffset() {
      return this.offset;
   }

   public long getId() {
      return this.id;
   }

   public long getBufferSize() {
      return this.bufferSize;
   }

   public long getDataPtr() {
      return this.dataPtr;
   }

   public void setBufferSize(long size) {
      this.bufferSize = size;
   }

   public void setId(long id) {
      this.id = id;
   }

   public void setAllocation(long allocation) {
      this.allocation = allocation;
   }

   public void setDataPtr(long dataPtr) {
      this.dataPtr = dataPtr;
   }

   public Buffer.BufferInfo getBufferInfo() {
      return new Buffer.BufferInfo(this.id, this.allocation, this.bufferSize, this.type.getType());
   }

   public record BufferInfo(long id, long allocation, long bufferSize, MemoryType.Type type) {
   }
}
