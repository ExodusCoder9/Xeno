package com.xeno.render.chunk.util;

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


import java.util.Arrays;
import java.util.Iterator;
import com.xeno.render.chunk.ChunkArea;

public record AreaSetQueue(int size, int[] set, StaticQueue<ChunkArea> queue) {
   public AreaSetQueue(int size) {
      this(size, new int[(int)Math.ceil(size / 32.0F)], new StaticQueue<>(size));
   }

   public AreaSetQueue {
   }

   public void add(ChunkArea chunkArea) {
      int i = chunkArea.index >> 5;
      int mask = 1 << (chunkArea.index & 31);
      if ((this.set[i] & mask) == 0) {
         this.queue.add(chunkArea);
         this.set[i] = this.set[i] | mask;
      }
   }

   public void clear() {
      Arrays.fill(this.set, 0);
      this.queue.clear();
   }

   public Iterator<ChunkArea> iterator(boolean reverseOrder) {
      return this.queue.iterator(reverseOrder);
   }

   public Iterator<ChunkArea> iterator() {
      return this.iterator(false);
   }
}
