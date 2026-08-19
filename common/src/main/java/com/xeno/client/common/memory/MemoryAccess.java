/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.common.memory;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.nio.ByteOrder;

/**
 * Utility class.
 * <p>
 * This class and its underlying implementation based on {@link sun.misc.Unsafe}
 * are explicitly marked as Deprecated by both us and
 * the Java platform (via OpenJDK JEP 471/498).
 * </p>
 * <p>
 * Despite this deprecation status, we will continue using it
 * across our mod due to its performance capabilities. We will not migrate to alternative
 * abstractions until sun.misc.Unsafe methods are entirely removed from Java.
 * </p>
 * <p>
 * That said, we will write future code to be more agnostic, ensuring it can easily be
 * patched or migrated to alternative APIs like VarHandle and FFM when Unsafe methods are removed.
 * </p>
 */
@Deprecated(since="0.1.0", forRemoval=true)
public class MemoryAccess {
    @Deprecated(since="0.1.0", forRemoval=true)
    private static final Unsafe UNSAFE;
    @Deprecated(since="0.1.0", forRemoval=true)
    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);

            UNSAFE = (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Strange! , Couldn't obtain reference to sun.misc.unsafe ", e);

        }
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static long fieldOffset(Class<?> owner, String fieldName) {
        try {
            return UNSAFE.objectFieldOffset(owner.getDeclaredField(fieldName));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found: " + owner.getName() + "." + fieldName, e);
        }
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static int getInt(Object target, long offset) {
        return UNSAFE.getInt(target, offset);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static float getFloat(long address) {
        return UNSAFE.getFloat(null, address);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static int getInt(long address) {
        return UNSAFE.getInt(null, address);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static byte getByte(long address) {
        return UNSAFE.getByte(null, address);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static long getLong(long address) {
        return UNSAFE.getLong(null, address);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static void putLong(long address, long value) {
        UNSAFE.putLong(null, address, value);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static void putFloat(long address, float value) {
        UNSAFE.putFloat(null, address, value);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static void putInt(long address, int value) {
        UNSAFE.putInt(null, address, value);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static void putShort(long address, short value) {
        UNSAFE.putShort(null, address, value);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static void putByte(long address, byte value) {
        UNSAFE.putByte(null, address, value);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static void copyMemory(long src, long dst, long length) {
        UNSAFE.copyMemory(src, dst, length);
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static long packFloats(float first, float second) {
        long packed = ((long) Float.floatToRawIntBits(second) << 32) | (Float.floatToRawIntBits(first) & 0xFFFFFFFFL);
        return IS_LITTLE_ENDIAN ? packed : Long.reverseBytes(((long) Float.floatToRawIntBits(first) << 32) | (Float.floatToRawIntBits(second) & 0xFFFFFFFFL));
    }

    @Deprecated(since="0.1.0", forRemoval=true)
    public static long packInts(int first, int second) {
        long packed = ((long) second << 32) | (first & 0xFFFFFFFFL);
        return IS_LITTLE_ENDIAN ? packed : Long.reverseBytes(((long) first << 32) | (second & 0xFFFFFFFFL));
    }
}
