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
package com.xeno.render.chunk.util;

import java.util.Iterator;
import org.apache.commons.lang3.Validate;

public class CircularIntList {
   private final int size;
   private final int[] list;
   private int startIndex;
   private final CircularIntList.OwnIterator iterator;
   private final CircularIntList.RangeIterator rangeIterator;

   public CircularIntList(int size) {
      this.size = size;
      this.list = new int[size + 2];
      this.iterator = new CircularIntList.OwnIterator();
      this.rangeIterator = new CircularIntList.RangeIterator();
   }

   public void updateStartIdx(int startIndex) {
      int[] list = this.list;
      this.startIndex = startIndex;
      list[0] = -1;
      list[this.size + 1] = -1;
      int k = 1;

      for (int i = startIndex; i < this.size; i++) {
         list[k] = i;
         k++;
      }

      for (int i = 0; i < startIndex; i++) {
         list[k] = i;
         k++;
      }
   }

   public int getNext(int i) {
      return this.list[i + 1];
   }

   public int getPrevious(int i) {
      return this.list[i - 1];
   }

   public CircularIntList.OwnIterator iterator() {
      return this.iterator;
   }

   public CircularIntList.RangeIterator getRangeIterator(int startIndex, int endIndex) {
      this.rangeIterator.update(startIndex, endIndex);
      return this.rangeIterator;
   }

   public CircularIntList.RangeIterator createRangeIterator() {
      return new CircularIntList.RangeIterator();
   }

   public class OwnIterator implements Iterator<Integer> {
      private int currentIndex = 0;
      private final int maxIndex = CircularIntList.this.size;

      @Override
      public boolean hasNext() {
         return this.currentIndex < this.maxIndex;
      }

      public Integer next() {
         this.currentIndex++;
         return CircularIntList.this.list[this.currentIndex];
      }

      public int getCurrentIndex() {
         return this.currentIndex;
      }

      public void restart() {
         this.currentIndex = 0;
      }
   }

   public class RangeIterator implements Iterator<Integer> {
      private int currentIndex;
      private int startIndex;
      private int endIndex;

      public void update(int startIndex, int endIndex) {
         Validate.isTrue(endIndex < CircularIntList.this.list.length, "Beyond max size", new Object[0]);
         this.startIndex = startIndex + 1;
         this.endIndex = endIndex + 1;
         this.restart();
      }

      @Override
      public boolean hasNext() {
         return this.currentIndex < this.endIndex;
      }

      public Integer next() {
         this.currentIndex++;
         return CircularIntList.this.list[this.currentIndex];
      }

      public int getCurrentIndex() {
         return this.currentIndex;
      }

      public void restart() {
         this.currentIndex = this.startIndex - 1;
      }
   }
}
