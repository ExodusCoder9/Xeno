package com.xeno.client.renderer.memory;

/**
 * Alias for XGenerationalArena.
 */
public class XenoGenerationalArena extends XGenerationalArena {
    public XenoGenerationalArena(int arenaId, XenoGeneration generation, String namePrefix, int gpuUsageFlags, long capacity) {
        super(arenaId, generation, XenoMemoryKind.GPU_VRAM, namePrefix, gpuUsageFlags, capacity);
    }
}
