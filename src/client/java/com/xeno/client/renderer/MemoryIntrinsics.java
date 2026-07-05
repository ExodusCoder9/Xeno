package com.xeno.client.renderer;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class MemoryIntrinsics {
    private MemoryIntrinsics() {}

    public static void putFloat(long address, float value) {
        MemorySegment.ofAddress(address).reinterpret(Float.BYTES).set(ValueLayout.JAVA_FLOAT, 0, value);
    }

    public static float getFloat(long address) {
        return MemorySegment.ofAddress(address).reinterpret(Float.BYTES).get(ValueLayout.JAVA_FLOAT, 0);
    }

    public static void putInt(long address, int value) {
        MemorySegment.ofAddress(address).reinterpret(Integer.BYTES).set(ValueLayout.JAVA_INT, 0, value);
    }

    public static int getInt(long address) {
        return MemorySegment.ofAddress(address).reinterpret(Integer.BYTES).get(ValueLayout.JAVA_INT, 0);
    }

    public static void putShort(long address, short value) {
        MemorySegment.ofAddress(address).reinterpret(Short.BYTES).set(ValueLayout.JAVA_SHORT, 0, value);
    }

    public static short getShort(long address) {
        return MemorySegment.ofAddress(address).reinterpret(Short.BYTES).get(ValueLayout.JAVA_SHORT, 0);
    }

    public static void putByte(long address, byte value) {
        MemorySegment.ofAddress(address).reinterpret(Byte.BYTES).set(ValueLayout.JAVA_BYTE, 0, value);
    }

    public static byte getByte(long address) {
        return MemorySegment.ofAddress(address).reinterpret(Byte.BYTES).get(ValueLayout.JAVA_BYTE, 0);
    }
}
