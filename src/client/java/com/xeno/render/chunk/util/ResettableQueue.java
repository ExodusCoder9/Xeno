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


import java.util.Iterator;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public class ResettableQueue<T> implements Iterable<T> {
   T[] queue;
   int position = 0;
   int limit = 0;
   int capacity;

   public ResettableQueue() {
      this(1024);
   }

   public ResettableQueue(int initialCapacity) {
      this.capacity = initialCapacity;
      this.queue = (T[])(new Object[this.capacity]);
   }

   public boolean hasNext() {
      return this.position < this.limit;
   }

   public T poll() {
      T t = this.queue[this.position];
      this.position++;
      return t;
   }

   public void add(T t) {
      this.queue[this.limit++] = t;
   }

   public void ensureCapacity(int n) {
      while (this.limit + n > this.capacity) {
         int newSize = Math.max(this.limit + n, this.capacity * 2);
         this.resize(newSize);
      }
   }

   private void resize(int capacity) {
      this.capacity = capacity;
      T[] oldQueue = this.queue;
      this.queue = (T[])(new Object[this.capacity]);
      System.arraycopy(oldQueue, 0, this.queue, 0, oldQueue.length);
   }

   public int size() {
      return this.limit;
   }

   public void rewind() {
      this.position = 0;
   }

   public T get(int i) {
      return this.queue[i];
   }

   public void clear() {
      this.position = 0;
      this.limit = 0;
   }

   public Iterator<T> iterator(boolean reverseOrder) {
      return reverseOrder ? new Iterator<T>() {
         int pos;
         final int limit;

         {
            this.pos = ResettableQueue.this.limit - 1;
            this.limit = -1;
         }

         @Override
         public boolean hasNext() {
            return this.pos > -1;
         }

         @Override
         public T next() {
            return ResettableQueue.this.queue[this.pos--];
         }
      } : new Iterator<T>() {
         int pos = 0;
         final int limit;

         {
            this.limit = ResettableQueue.this.limit;
         }

         @Override
         public boolean hasNext() {
            return this.pos < this.limit;
         }

         @Override
         public T next() {
            return ResettableQueue.this.queue[this.pos++];
         }
      };
   }

   @NotNull
   @Override
   public Iterator<T> iterator() {
      return this.iterator(false);
   }

   @Override
   public void forEach(Consumer<? super T> action) {
      for (int i = 0; i < this.limit; i++) {
         action.accept(this.queue[i]);
      }
   }
}
