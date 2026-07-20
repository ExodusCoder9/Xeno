package com.xeno.client.renderer.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

/**
 * Provides zero allocation , Intrinsic memory access using the Foreign Function & Memory API.
 */
public final class MemoryIntrinsics {
    private static final MemorySegment HEAP = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE);

    private static final ValueLayout.OfInt INT_UNALIGNED = ValueLayout.JAVA_INT.withByteAlignment(1);
    private static final ValueLayout.OfShort SHORT_UNALIGNED = ValueLayout.JAVA_SHORT.withByteAlignment(1);
    private static final ValueLayout.OfByte BYTE_UNALIGNED = ValueLayout.JAVA_BYTE;
    private static final ValueLayout.OfFloat FLOAT_UNALIGNED = ValueLayout.JAVA_FLOAT.withByteAlignment(1);

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

    public static void putFloat(long address, float value) {
        HEAP.set(FLOAT_UNALIGNED, address, value);
    }

    public static byte getByte(long address) {
        return HEAP.get(BYTE_UNALIGNED, address);
    }

    /**
     * Copies data from a ByteBuffer directly into native memory.
     * The ofBuffer() method wraps the buffer from its current position, offset must be 0L.
     */
    public static void copy(ByteBuffer src, long destAddress, long length) {
        MemorySegment srcSegment = MemorySegment.ofBuffer(src);
        MemorySegment.copy(srcSegment, 0L, HEAP, destAddress, length);
    }

    /**
     * Copies data from one ByteBuffer to another ByteBuffer using MemorySegment.
     */
    public static void copy(ByteBuffer src, ByteBuffer dest, long length) {
        MemorySegment srcSegment = MemorySegment.ofBuffer(src);
        MemorySegment destSegment = MemorySegment.ofBuffer(dest);
        MemorySegment.copy(srcSegment, 0L, destSegment, 0L, length);
    }
}