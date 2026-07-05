package com.xeno.client.renderer;

import com.mojang.blaze3d.vertex.TlsfAllocator;
import java.util.IdentityHashMap;
import java.util.Map;

public class XenoAllocationTracker {
    private static final Map<Object, TlsfAllocator.Allocation> vertexTlsfMap = new IdentityHashMap<>();
    private static final Map<Object, TlsfAllocator.Allocation> indexTlsfMap = new IdentityHashMap<>();

    public static synchronized void register(Object key, TlsfAllocator.Allocation allocation, boolean isVertex) {
        if (isVertex) {
            vertexTlsfMap.put(key, allocation);
        } else {
            indexTlsfMap.put(key, allocation);
        }
    }

    public static synchronized TlsfAllocator.Allocation getAllocation(Object key, boolean isVertex) {
        return isVertex ? vertexTlsfMap.get(key) : indexTlsfMap.get(key);
    }

    public static synchronized void unregister(Object key, boolean isVertex) {
        if (isVertex) {
            vertexTlsfMap.remove(key);
        } else {
            indexTlsfMap.remove(key);
        }
    }
}
