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
package com.xeno.render.util;

import it.unimi.dsi.fastutil.ints.IntComparator;
import java.util.Arrays;

public class SortUtil {
   public static void mergeSort(int[] a, float[] distances) {
      mergeSort(a, distances, 0, a.length, null);
   }

   public static void mergeSort(int[] indices, float[] distances, int from, int to, int[] supp) {
      int len = to - from;
      if (len < 16) {
         insertionSort(indices, distances, from, to);
      } else {
         if (supp == null) {
            supp = Arrays.copyOf(indices, to);
         }

         int mid = from + to >>> 1;
         mergeSort(supp, distances, from, mid, indices);
         mergeSort(supp, distances, mid, to, indices);
         if (Float.compare(distances[supp[mid]], distances[supp[mid - 1]]) <= 0) {
            System.arraycopy(supp, from, indices, from, len);
         } else {
            int i = from;
            int p = from;
            int q = mid;

            while (i < to) {
               if (q >= to || p < mid && Float.compare(distances[supp[q]], distances[supp[p]]) <= 0) {
                  indices[i] = supp[p++];
               } else {
                  indices[i] = supp[q++];
               }

               i++;
            }
         }
      }
   }

   public static void quickSort(int[] a, float[] distances) {
      quickSort(a, distances, 0, a.length);
   }

   public static void quickSort(int[] is, float[] distances, int from, int to) {
      int len = to - from;
      if (len < 16) {
         insertionSort(is, distances, from, to);
      } else {
         int m = from + len / 2;
         int l = from;
         int n = to - 1;
         int ab = Float.compare(distances[is[l]], distances[is[m]]);
         int ac = Float.compare(distances[is[l]], distances[is[n]]);
         int bc = Float.compare(distances[is[m]], distances[is[n]]);
         m = ab < 0 ? (bc < 0 ? m : (ac < 0 ? n : l)) : (bc > 0 ? m : (ac > 0 ? n : l));
         int v = is[m];
         int a = from;
         int b = from;
         int c = to - 1;
         int d = c;
         swap(is, m, d);
         float mValue = distances[v];

         while (b < c) {
            if (Float.compare(distances[is[b]], mValue) > 0) {
               while (b < c) {
                  if (Float.compare(distances[is[c]], mValue) < 0) {
                     swap(is, b, c);
                     b++;
                     c--;
                     break;
                  }

                  c--;
               }
            } else {
               b++;
            }
         }

         swap(is, d, b);
         if (b - a > 1) {
            quickSort(is, distances, a, b);
         }

         if (d - b > 1) {
            quickSort(is, distances, b, d);
         }
      }
   }

   private static void insertionSort(int[] is, float[] distances, int from, int to) {
      int i = from;

      while (++i < to) {
         int t = is[i];
         int j = i;

         for (int u = is[i - 1]; Float.compare(distances[u], distances[t]) < 0; u = is[j - 1]) {
            is[j] = u;
            if (from == j - 1) {
               j--;
               break;
            }

            j--;
         }

         is[j] = t;
      }
   }

   public static void swap(int[] x, int a, int b, int n) {
      int i = 0;

      while (i < n) {
         swap(x, a, b);
         b++;
         i++;
         a++;
      }
   }

   public static void swap(int[] x, int a, int b) {
      int t = x[a];
      x[a] = x[b];
      x[b] = t;
   }

   private static int med3(int[] x, int a, int b, int c, IntComparator comp) {
      int ab = comp.compare(x[a], x[b]);
      int ac = comp.compare(x[a], x[c]);
      int bc = comp.compare(x[b], x[c]);
      return ab < 0 ? (bc < 0 ? b : (ac < 0 ? c : a)) : (bc > 0 ? b : (ac > 0 ? c : a));
   }
}
