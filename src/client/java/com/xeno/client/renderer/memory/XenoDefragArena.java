package com.xeno.client.renderer.memory;

/**
 * Legacy alias for XGenerationalArena.
 */
public class XenoDefragArena extends XGenerationalArena {
    public XenoDefragArena(int arenaId, XenoGeneration generation, String namePrefix, int gpuUsageFlags, long capacity) {
        super(arenaId, generation, XenoMemoryKind.GPU_VRAM, namePrefix, gpuUsageFlags, capacity);
    }
}
