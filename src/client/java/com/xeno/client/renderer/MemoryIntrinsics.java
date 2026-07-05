package com.xeno.client.renderer;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class MemoryIntrinsics {
    private static final MemorySegment HEAP = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE);

    private static final ValueLayout.OfInt INT_UNALIGNED = ValueLayout.JAVA_INT.withByteAlignment(1);

    private static final ValueLayout.OfShort SHORT_UNALIGNED = ValueLayout.JAVA_SHORT.withByteAlignment(1);

    private static final ValueLayout.OfByte BYTE_UNALIGNED = ValueLayout.JAVA_BYTE;

    private MemoryIntrinsics() {}

    public static void putInt(long address, int value) {
        HEAP.set(INT_UNALIGNED, address, value);
    }

    public static void putShort(long address, short value) {
        HEAP.set(SHORT_UNALIGNED, address, value);
    }

    public static void putByte(long address, byte value) {
        HEAP.set(BYTE_UNALIGNED, address, value);
    }

    public static byte getByte(long address) {
        return HEAP.get(BYTE_UNALIGNED, address);
    }

    public static void copy(java.nio.ByteBuffer src, long destAddress, long length) {
        MemorySegment srcSegment = MemorySegment.ofBuffer(src);
        MemorySegment.copy(srcSegment, (long) src.position(), HEAP, destAddress, length);
    }
}
