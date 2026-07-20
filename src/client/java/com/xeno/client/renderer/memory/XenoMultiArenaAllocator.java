package com.xeno.client.renderer.memory;

/**
 * Legacy alias for XGenerationalMultiBufferAllocator.
 */
public class XenoMultiArenaAllocator extends XGenerationalMultiBufferAllocator {
    public XenoMultiArenaAllocator(String namePrefix, int usage, long youngArenaCapacity, long oldArenaCapacity) {
        super(namePrefix, usage, youngArenaCapacity, oldArenaCapacity);
    }
}
