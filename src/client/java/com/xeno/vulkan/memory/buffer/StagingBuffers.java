package com.xeno.vulkan.memory.buffer;

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


import it.unimi.dsi.fastutil.Stack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;

public class StagingBuffers {
   final Stack<StagingBuffer> availableBuffers = new ObjectArrayList();
   ObjectArrayList<StagingBuffer>[] usedBuffersByFrame;
   StagingBuffer currentBuffer;
   boolean inFrame = false;

   public StagingBuffers() {
   }

   public void updateFrameCount(int frames) {
      if (this.usedBuffersByFrame != null) {
         for (ObjectArrayList<StagingBuffer> bufferList : this.usedBuffersByFrame) {
            ObjectListIterator var6 = bufferList.iterator();

            while (var6.hasNext()) {
               StagingBuffer buffer = (StagingBuffer)var6.next();
               this.availableBuffers.push(buffer);
            }
         }
      } else {
         for (int i = 0; i < frames + 1; i++) {
            this.availableBuffers.push(new StagingBuffer());
         }
      }

      this.currentBuffer = null;
      this.usedBuffersByFrame = new ObjectArrayList[frames];

      for (int i = 0; i < frames; i++) {
         this.usedBuffersByFrame[i] = new ObjectArrayList();
      }
   }

   public StagingBuffer getStagingBuffer() {
      if (this.currentBuffer == null) {
         if (this.availableBuffers.isEmpty()) {
            this.availableBuffers.push(new StagingBuffer());
         }

         this.currentBuffer = (StagingBuffer)this.availableBuffers.pop();
      }

      if (!this.inFrame) {
         System.nanoTime();
      }

      return this.currentBuffer;
   }

   public void beginFrame(int frame) {
      ObjectArrayList<StagingBuffer> usedBuffers = this.usedBuffersByFrame[frame];
      ObjectListIterator var3 = usedBuffers.iterator();

      while (var3.hasNext()) {
         StagingBuffer buffer = (StagingBuffer)var3.next();
         buffer.reset();
         this.availableBuffers.push(buffer);
      }

      usedBuffers.clear();
      if (this.currentBuffer != null) {
         usedBuffers.push(this.currentBuffer);
      }

      if (this.availableBuffers.isEmpty()) {
         this.availableBuffers.push(new StagingBuffer());
      }

      this.currentBuffer = (StagingBuffer)this.availableBuffers.pop();
      this.inFrame = true;
   }

   public void endFrame(int frame) {
      this.usedBuffersByFrame[frame].push(this.currentBuffer);
      this.currentBuffer = null;
      this.inFrame = false;
   }

   public void free() {
      for (ObjectArrayList<StagingBuffer> bufferList : this.usedBuffersByFrame) {
         ObjectListIterator var5 = bufferList.iterator();

         while (var5.hasNext()) {
            StagingBuffer buffer = (StagingBuffer)var5.next();
            buffer.scheduleFree();
         }
      }

      while (!this.availableBuffers.isEmpty()) {
         ((StagingBuffer)this.availableBuffers.pop()).scheduleFree();
      }
   }
}
