package com.xeno.client.renderer.chunk;

import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.memory.XGenerationalMultiBufferAllocator;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Fast Spatial Chunk Section Storage & Lifecycle Manager.
 * Maintains a 1D spatial hash map of chunk section nodes, completely bypassing
 * Mojang's ViewArea modulo array wrapping. Releases GPU VRAM allocations instantly
 * on chunk unload without waiting for Java Garbage Collection.
 */
@SuppressWarnings("unused")
public class XenoSectionStorage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final XenoSectionStorage INSTANCE = new XenoSectionStorage();

    // Map section coordinate packed key -> XenoSectionNode
    private final Map<Long, XenoSectionNode> sectionGrid = new ConcurrentHashMap<>();

    public static XenoSectionStorage getInstance() {
        return INSTANCE;
    }

    public record XenoSectionNode(
            int sectionX,
            int sectionY,
            int sectionZ,
            XGenerationalMultiBufferAllocator.AllocationHandle vramAllocHandle
    ) {}

    public static long packKey(int x, int y, int z) {
        return (((long) x & 0x3FFFFF) << 42) | (((long) y & 0xFFFFF) << 22) | ((long) z & 0x3FFFFF);
    }

    public XenoSectionNode getSection(int x, int y, int z) {
        return this.sectionGrid.get(packKey(x, y, z));
    }

    public void putSection(int x, int y, int z, XGenerationalMultiBufferAllocator.AllocationHandle handle) {
        long key = packKey(x, y, z);
        XenoSectionNode old = this.sectionGrid.put(key, new XenoSectionNode(x, y, z, handle));
        if (old != null && old.vramAllocHandle() != null) {
            XenoWorldRenderer.getOffHeapBuildingPool().free(old.vramAllocHandle());
        }
    }

    /**
     * Called instantly on chunk unload (e.g. ClientChunkCache.drop).
     * Frees all GPU VRAM allocations for all 16-24 vertical sections of the chunk immediately.
     */
    public void onChunkUnload(int chunkX, int chunkZ) {
        int freedCount = 0;
        for (int secY = -4; secY <= 20; secY++) {
            long key = packKey(chunkX, secY, chunkZ);
            XenoSectionNode node = this.sectionGrid.remove(key);
            if (node != null && node.vramAllocHandle() != null) {
                XenoWorldRenderer.getOffHeapBuildingPool().free(node.vramAllocHandle());
                freedCount++;
            }
        }
        if (freedCount > 0) {
            LOGGER.debug("[Xeno] Instantly freed VRAM for {} sections in chunk ({}, {})", freedCount, chunkX, chunkZ);
        }
    }

    public void clearAll() {
        for (XenoSectionNode node : this.sectionGrid.values()) {
            if (node.vramAllocHandle() != null) {
                XenoWorldRenderer.getOffHeapBuildingPool().free(node.vramAllocHandle());
            }
        }
        this.sectionGrid.clear();
    }
}
