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
package com.xeno.vulkan.util;

import java.lang.reflect.Field;
import org.lwjgl.system.Pointer;
import sun.misc.Unsafe;

public class MemoryAccess {
   public static final Unsafe UNSAFE;
   public static final boolean BITS64 = Pointer.BITS64;

   public static boolean memGetBoolean(long ptr) {
      return UNSAFE.getByte(null, ptr) != 0;
   }

   public static byte memGetByte(long ptr) {
      return UNSAFE.getByte(null, ptr);
   }

   public static short memGetShort(long ptr) {
      return UNSAFE.getShort(null, ptr);
   }

   public static int memGetInt(long ptr) {
      return UNSAFE.getInt(null, ptr);
   }

   public static long memGetLong(long ptr) {
      return UNSAFE.getLong(null, ptr);
   }

   public static float memGetFloat(long ptr) {
      return UNSAFE.getFloat(null, ptr);
   }

   public static double memGetDouble(long ptr) {
      return UNSAFE.getDouble(null, ptr);
   }

   public static long memGetAddress(long ptr) {
      return BITS64 ? UNSAFE.getLong(null, ptr) : UNSAFE.getInt(null, ptr) & 4294967295L;
   }

   public static void memPutByte(long ptr, byte value) {
      UNSAFE.putByte(null, ptr, value);
   }

   public static void memPutShort(long ptr, short value) {
      UNSAFE.putShort(null, ptr, value);
   }

   public static void memPutInt(long ptr, int value) {
      UNSAFE.putInt(null, ptr, value);
   }

   public static void memPutLong(long ptr, long value) {
      UNSAFE.putLong(null, ptr, value);
   }

   public static void memPutFloat(long ptr, float value) {
      UNSAFE.putFloat(null, ptr, value);
   }

   public static void memPutDouble(long ptr, double value) {
      UNSAFE.putDouble(null, ptr, value);
   }

   static {
      try {
         Field f = Unsafe.class.getDeclaredField("theUnsafe");
         f.setAccessible(true);
         UNSAFE = (Unsafe)f.get(null);
      } catch (NoSuchFieldException | IllegalAccessException e) {
         throw new RuntimeException(e);
      }
   }
}
