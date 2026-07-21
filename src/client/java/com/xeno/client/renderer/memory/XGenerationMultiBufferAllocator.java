package com.xeno.client.renderer.memory;

/**
 * Convenience alias for XGenerationalMultiBufferAllocator.
 */
public class XGenerationMultiBufferAllocator extends XGenerationalMultiBufferAllocator {
    public XGenerationMultiBufferAllocator(String namePrefix, XenoMemoryKind memoryKind, int gpuUsageFlags, long youngCapacity, long survivorCapacity, long oldCapacity) {
        super(namePrefix, memoryKind, gpuUsageFlags, youngCapacity, survivorCapacity, oldCapacity);
    }

    public XGenerationMultiBufferAllocator(String namePrefix, int usage, long youngArenaCapacity, long oldArenaCapacity) {
        super(namePrefix, usage, youngArenaCapacity, oldArenaCapacity);
    }
}
