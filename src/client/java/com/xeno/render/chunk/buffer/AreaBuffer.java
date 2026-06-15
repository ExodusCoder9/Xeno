package com.xeno.render.chunk.buffer;

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


import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.nio.ByteBuffer;
import com.xeno.Initializer;
import com.xeno.render.chunk.util.Util;
import com.xeno.vulkan.memory.MemoryManager;
import com.xeno.vulkan.memory.MemoryType;
import com.xeno.vulkan.memory.MemoryTypes;
import com.xeno.vulkan.memory.buffer.Buffer;
import com.xeno.vulkan.memory.buffer.IndexBuffer;
import com.xeno.vulkan.memory.buffer.VertexBuffer;
import org.apache.logging.log4j.Logger;

public class AreaBuffer {
   private static final boolean DEBUG = false;
   private static final Logger LOGGER = Initializer.LOGGER;
   private static final MemoryType MEMORY_TYPE = MemoryTypes.GPU_MEM;
   private final int usage;
   private final int elementSize;
   private final Int2ReferenceOpenHashMap<AreaBuffer.Segment> usedSegments = new Int2ReferenceOpenHashMap();
   private Segment root;
   private Segment segmentPool;
   AreaBuffer.Segment first;
   AreaBuffer.Segment last;
   private Buffer buffer;
   int size;
   int used = 0;
   int segments = 0;

   private Segment obtainSegment(int offset, int size) {
      if (segmentPool != null) {
         Segment s = segmentPool;
         segmentPool = s.next;
         s.offset = offset;
         s.size = size;
         s.free = true;
         s.paramsPtr = 0L;
         s.next = null;
         s.prev = null;
         s.left = null;
         s.right = null;
         s.parent = null;
         s.sameSizeNext = null;
         s.sameSizePrev = null;
         return s;
      }
      return new Segment(offset, size);
   }

   private void releaseSegment(Segment s) {
      s.next = segmentPool;
      s.prev = null;
      s.left = null;
      s.right = null;
      s.parent = null;
      s.sameSizeNext = null;
      s.sameSizePrev = null;
      segmentPool = s;
   }

   private void rotateLeft(Segment x) {
      Segment y = x.right;
      x.right = y.left;
      if (y.left != null) y.left.parent = x;
      y.parent = x.parent;
      if (x.parent == null) {
         root = y;
      } else if (x == x.parent.left) {
         x.parent.left = y;
      } else {
         x.parent.right = y;
      }
      y.left = x;
      x.parent = y;
   }

   private void rotateRight(Segment x) {
      Segment y = x.left;
      x.left = y.right;
      if (y.right != null) y.right.parent = x;
      y.parent = x.parent;
      if (x.parent == null) {
         root = y;
      } else if (x == x.parent.right) {
         x.parent.right = y;
      } else {
         x.parent.left = y;
      }
      y.right = x;
      x.parent = y;
   }

   private void addFreeSegment(Segment s) {
      s.left = null;
      s.right = null;
      s.parent = null;
      s.priority = java.util.concurrent.ThreadLocalRandom.current().nextInt();
      s.sameSizeNext = null;
      s.sameSizePrev = null;

      if (root == null) {
         root = s;
         return;
      }

      Segment curr = root;
      Segment parent = null;
      while (curr != null) {
         parent = curr;
         if (s.size == curr.size) {
            s.sameSizeNext = curr.sameSizeNext;
            s.sameSizePrev = curr;
            if (curr.sameSizeNext != null) {
               curr.sameSizeNext.sameSizePrev = s;
            }
            curr.sameSizeNext = s;
            return;
         } else if (s.size < curr.size) {
            curr = curr.left;
         } else {
            curr = curr.right;
         }
      }

      s.parent = parent;
      if (s.size < parent.size) {
         parent.left = s;
      } else {
         parent.right = s;
      }

      while (s.parent != null && s.priority < s.parent.priority) {
         if (s == s.parent.left) {
            rotateRight(s.parent);
         } else {
            rotateLeft(s.parent);
         }
      }
   }

   private void removeFreeSegment(Segment s) {
      if (s.sameSizePrev != null) {
         s.sameSizePrev.sameSizeNext = s.sameSizeNext;
         if (s.sameSizeNext != null) {
            s.sameSizeNext.sameSizePrev = s.sameSizePrev;
         }
         s.sameSizeNext = null;
         s.sameSizePrev = null;
         return;
      }

      if (s.sameSizeNext != null) {
         Segment nextNode = s.sameSizeNext;
         nextNode.left = s.left;
         if (s.left != null) s.left.parent = nextNode;
         nextNode.right = s.right;
         if (s.right != null) s.right.parent = nextNode;
         nextNode.parent = s.parent;
         if (s.parent == null) {
            root = nextNode;
         } else if (s == s.parent.left) {
            s.parent.left = nextNode;
         } else {
            s.parent.right = nextNode;
         }
         nextNode.priority = s.priority;
         nextNode.sameSizePrev = null;
         s.left = null;
         s.right = null;
         s.parent = null;
         s.sameSizeNext = null;
         return;
      }

      while (s.left != null || s.right != null) {
         if (s.left == null) {
            rotateLeft(s);
         } else if (s.right == null) {
            rotateRight(s);
         } else if (s.left.priority < s.right.priority) {
            rotateRight(s);
         } else {
            rotateLeft(s);
         }
      }

      if (s.parent == null) {
         root = null;
      } else {
         if (s == s.parent.left) {
            s.parent.left = null;
         } else {
            s.parent.right = null;
         }
         s.parent = null;
      }
   }

   public AreaBuffer(AreaBuffer.Usage usage, int elementCount, int elementSize) {
      this.usage = usage.usage;
      this.elementSize = elementSize;
      this.size = elementCount * elementSize;
      this.buffer = this.allocateBuffer();
      AreaBuffer.Segment s = this.obtainSegment(0, this.size);
      this.segments++;
      this.last = this.first = s;
      this.addFreeSegment(s);
   }

   private Buffer allocateBuffer() {
      Buffer buffer;
      if (this.usage == AreaBuffer.Usage.VERTEX.usage) {
         buffer = new VertexBuffer(this.size, MEMORY_TYPE);
      } else {
         buffer = new IndexBuffer(this.size, MEMORY_TYPE);
      }

      return buffer;
   }

   public AreaBuffer.Segment allocateSegment(int size) {
      AreaBuffer.Segment segment = this.findSegment(size);
      this.removeFreeSegment(segment);
      if (segment.size - size > 0) {
         AreaBuffer.Segment s1 = this.obtainSegment(segment.offset + size, segment.size - size);
         this.segments++;
         if (segment.next != null) {
            s1.bindNext(segment.next);
         } else {
            this.last = s1;
         }

         segment.bindNext(s1);
         segment.size = size;
         this.addFreeSegment(s1);
      }

      segment.free = false;
      this.usedSegments.put(segment.offset, segment);
      segment.paramsPtr = 0L;
      this.used += size;
      return segment;
   }

   public void freeSegment(int offset) {
      if (offset != -1) {
         MemoryManager.getInstance().addToFreeSegment(this, offset);
      }
   }

   public void upload(AreaBuffer.Segment segment, ByteBuffer byteBuffer, int offset) {
      int size = byteBuffer.remaining();
      if (size + offset > segment.size) {
         throw new RuntimeException("trying to upload %d at offset %d, but segment size is %d".formatted(size, offset, segment.size));
      }

      Buffer dst = this.buffer;
      UploadManager.INSTANCE.recordUpload(dst, segment.offset + offset, size, byteBuffer);
   }

   public AreaBuffer.Segment upload(ByteBuffer byteBuffer, int oldOffset, long paramsPtr) {
      this.freeSegment(oldOffset);
      int size = byteBuffer.remaining();
      AreaBuffer.Segment segment = this.findSegment(size);
      this.removeFreeSegment(segment);
      if (segment.size - size > 0) {
         AreaBuffer.Segment s1 = this.obtainSegment(segment.offset + size, segment.size - size);
         this.segments++;
         if (segment.next != null) {
            s1.bindNext(segment.next);
         } else {
            this.last = s1;
         }

         segment.bindNext(s1);
         segment.size = size;
         this.addFreeSegment(s1);
      }

      segment.free = false;
      this.usedSegments.put(segment.offset, segment);
      segment.paramsPtr = paramsPtr;
      Buffer dst = this.buffer;
      UploadManager.INSTANCE.recordUpload(dst, segment.offset, size, byteBuffer);
      this.used += size;
      return segment;
   }

   public AreaBuffer.Segment findSegment(int size) {
      Segment curr = root;
      Segment best = null;
      while (curr != null) {
         if (curr.size == size) {
            return curr;
         } else if (curr.size > size) {
            best = curr;
            curr = curr.left;
         } else {
            curr = curr.right;
         }
      }
      return best != null ? best : this.reallocate(size);
   }

   public AreaBuffer.Segment reallocate(int uploadSize) {
      int oldSize = this.size;
      int minIncrement = this.size >> 1;
      minIncrement = (int)Util.align(minIncrement, this.elementSize);
      int increment = Math.max(minIncrement, uploadSize);
      if (increment < uploadSize) {
         throw new RuntimeException(String.format("Size increment %d < %d (Upload size)", increment, uploadSize));
      }

      int newSize = oldSize + increment;
      this.size = newSize;
      Buffer dst = this.allocateBuffer();
      UploadManager.INSTANCE.copyBuffer(this.buffer, dst);
      this.buffer.scheduleFree();
      this.buffer = dst;
      if (this.last.isFree()) {
         this.removeFreeSegment(this.last);
         this.last.size += increment;
         this.addFreeSegment(this.last);
      } else {
         int offset = this.last.offset + this.last.size;
         AreaBuffer.Segment segment = this.obtainSegment(offset, newSize - offset);
         this.segments++;
         this.last.bindNext(segment);
         this.last = segment;
         this.addFreeSegment(segment);
      }

      return this.last;
   }

   void moveUsedSegments(Buffer dst) {
      this.root = null;
      int usedCount = 0;
      int dstOffset = 0;
      int currOffset = dstOffset;
      AreaBuffer.Segment segment = this.first;
      AreaBuffer.Segment prevUsed = null;
      int srcOffset = -1;
      int uploadSize = 0;

      while (segment != null) {
         AreaBuffer.Segment next = segment.next;
         if (!segment.isFree()) {
            usedCount++;
            if (segment.offset != srcOffset + uploadSize) {
               if (srcOffset == -1) {
                  dstOffset = 0;
                  this.first = segment;
                  segment.prev = null;
               } else {
                  UploadManager.INSTANCE.copyBuffer(this.buffer, srcOffset, dst, dstOffset, uploadSize);
                  dstOffset += uploadSize;
               }

               srcOffset = segment.offset;
               uploadSize = segment.size;
            } else {
               uploadSize += segment.size;
            }

            this.usedSegments.remove(segment.offset);
            segment.offset = currOffset;
            currOffset += segment.size;
            this.updateDrawParams(segment);
            this.usedSegments.put(segment.offset, segment);
            if (prevUsed != null) {
               prevUsed.bindNext(segment);
            }

            prevUsed = segment;
         } else {
            this.releaseSegment(segment);
         }

         segment = next;
      }

      if (uploadSize > 0) {
         UploadManager.INSTANCE.copyBuffer(this.buffer, srcOffset, dst, dstOffset, uploadSize);
      }

      if (prevUsed != null) {
         prevUsed.next = null;
         this.last = prevUsed;
         this.segments = usedCount;
      }
   }

   public void setSegmentFree(int offset) {
      AreaBuffer.Segment segment = (AreaBuffer.Segment)this.usedSegments.remove(offset * this.elementSize);
      if (segment != null) {
         this.used = this.used - segment.size;
         segment.free = true;
         segment.paramsPtr = -1L;
         AreaBuffer.Segment next = segment.next;
         if (next != null && next.isFree()) {
            this.removeFreeSegment(next);
            this.mergeSegments(segment, next);
         }

         AreaBuffer.Segment prev = segment.prev;
         if (prev != null && prev.isFree()) {
            this.removeFreeSegment(prev);
            this.mergeSegments(prev, segment);
            segment = prev;
         }

         this.addFreeSegment(segment);
      }
   }

   private void mergeSegments(AreaBuffer.Segment segment, AreaBuffer.Segment next) {
      segment.size = segment.size + next.size;
      if (next.next != null) {
         next.next.prev = segment;
      } else {
         this.last = segment;
      }

      segment.next = next.next;
      this.segments--;
      this.releaseSegment(next);
   }

   private void updateDrawParams(AreaBuffer.Segment segment) {
      int elementOffset = segment.offset / this.elementSize;
      if (this.usage == AreaBuffer.Usage.VERTEX.usage) {
         DrawParametersBuffer.setVertexOffset(segment.paramsPtr, elementOffset);
      } else {
         DrawParametersBuffer.setFirstIndex(segment.paramsPtr, elementOffset);
      }
   }

   public long getId() {
      return this.buffer.getId();
   }

   public void freeBuffer() {
      this.buffer.scheduleFree();
   }

   public int fragmentation() {
      return this.size - this.used - (this.last.isFree() ? this.last.size : 0);
   }

   public void checkSegments() {
      AreaBuffer.Segment segment = this.first;
      AreaBuffer.Segment prev = null;
      int i = 0;
      int usedSegments = 0;
      if (segment.offset != 0) {
         LOGGER.error(String.format("expected first offset 0 but got %d", segment.offset));
      }

      while (segment != null) {
         if (i >= this.segments) {
            LOGGER.error("Count is greater than segments");
            break;
         }

         if (segment.prev != prev) {
            LOGGER.error(String.format("expected previous segment not matching (segment %d)", i));
         }

         if (!segment.isFree()) {
            usedSegments++;
         }

         if (segment.offset % this.elementSize != 0) {
            LOGGER.error(String.format("offset %d misaligned (segment %d)", segment.offset, i));
         }

         AreaBuffer.Segment next = segment.next;
         if (next != null) {
            int offset = segment.offset + segment.size;
            if (offset != next.offset) {
               LOGGER.error(String.format("expected offset %d but got %d (segment %d)", offset, next.offset, i));
            }

            if (next.prev != segment) {
               LOGGER.error(String.format("segment pointer not correct (segment %d)", i));
            }
         } else if (segment != this.last) {
            LOGGER.error(String.format("segment has no next pointer and it's not last (segment %d)", i));
         } else {
            int segmentEnd = segment.offset + segment.size;
            if (segment.offset + segment.size != this.size) {
               LOGGER.error(String.format("last segment end (%d) does not match buffer size (%d)", segmentEnd, this.size));
            }

            if (segment.offset != this.used) {
               LOGGER.error(String.format("last segment offset (%d) does not match buffer used size (%d)", segment.offset, this.size));
            }
         }

         prev = segment;
         segment = next;
         i++;
      }

      if (i != this.segments) {
         LOGGER.error("Count do not match segments");
      }

      if (usedSegments != this.usedSegments.size()) {
         LOGGER.error("Counted used segment do not match used segments map size");
      }

      int totalFreeInTreap = countFreeSegmentsInTreap(root);
      int totalFreeSegments = 0;
      Segment curr = first;
      while (curr != null) {
         if (curr.isFree()) {
            totalFreeSegments++;
         }
         curr = curr.next;
      }
      if (totalFreeSegments != totalFreeInTreap) {
         LOGGER.error(String.format("Total free segments in physical list (%d) does not match Treap total (%d)", totalFreeSegments, totalFreeInTreap));
      }
   }

   private int countFreeSegmentsInTreap(Segment node) {
      if (node == null) return 0;
      int count = 1;
      Segment s = node.sameSizeNext;
      while (s != null) {
         count++;
         s = s.sameSizeNext;
      }
      return count + countFreeSegmentsInTreap(node.left) + countFreeSegmentsInTreap(node.right);
   }

   public int getSize() {
      return this.size;
   }

   public int getUsed() {
      return this.used;
   }

   public static class Segment {
      int offset;
      int size;
      boolean free = true;
      long paramsPtr;
      AreaBuffer.Segment next;
      AreaBuffer.Segment prev;

      // Treap pointers for free segments tree
      Segment left;
      Segment right;
      Segment parent;
      int priority;

      // Chain pointers for free segments of the same size
      Segment sameSizeNext;
      Segment sameSizePrev;

      private Segment(int offset, int size) {
         this.offset = offset;
         this.size = size;
      }

      public int getOffset() {
         return this.offset;
      }

      public int getSize() {
         return this.size;
      }

      public boolean isFree() {
         return this.free;
      }

      public void setFree(boolean free) {
         this.free = free;
      }

      public void bindNext(AreaBuffer.Segment s) {
         this.next = s;
         s.prev = this;
      }
   }

   public enum Usage {
      VERTEX(0),
      INDEX(1);

      final int usage;

      Usage(int i) {
         this.usage = i;
      }
   }
}
