package com.xeno.client.renderer.memory;

/**
 * High-performance bit-packed handle encoding for 0-GC primitive allocations.
 * Bit allocation (64-bit long):
 * - Bits 0..35  (36 bits): Byte Offset within arena (Up to 64 GB per arena)
 * - Bits 36..51 (16 bits): Aligned Allocation Size (Up to 64 KB per allocation)
 * - Bits 52..63 (12 bits): Arena ID (Up to 4096 active arenas per tier)
 */
public final class XenoHandle {
    private static final long OFFSET_MASK = (1L << 36) - 1;
    private static final long SIZE_MASK = (1L << 16) - 1;
    private static final long ARENA_MASK = (1L << 12) - 1;

    public static final long NULL_HANDLE = 0L;

    private XenoHandle() {}

    public static long pack(int arenaId, long offset, long size) {
        return ((long) (arenaId & ARENA_MASK) << 52)
                | ((size & SIZE_MASK) << 36)
                | (offset & OFFSET_MASK);
    }

    public static int getArenaId(long handle) {
        return (int) ((handle >>> 52) & ARENA_MASK);
    }

    public static long getSize(long handle) {
        return (handle >>> 36) & SIZE_MASK;
    }

    public static long getOffset(long handle) {
        return handle & OFFSET_MASK;
    }

    public static boolean isValid(long handle) {
        return handle != NULL_HANDLE;
    }
}
