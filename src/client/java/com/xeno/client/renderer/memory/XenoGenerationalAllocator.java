package com.xeno.client.renderer.memory;

/**
 * Alias for XGenerationalMultiBufferAllocator.
 */
public class XenoGenerationalAllocator extends XGenerationalMultiBufferAllocator {
    public XenoGenerationalAllocator(String namePrefix, int usage, long youngArenaCapacity, long oldArenaCapacity) {
        super(namePrefix, usage, youngArenaCapacity, oldArenaCapacity);
    }
}
