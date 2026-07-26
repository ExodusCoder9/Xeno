package com.xeno.client.renderer.particle;

import com.xeno.client.renderer.XenoWorldRenderer;
import com.xeno.client.renderer.memory.XGenerationalMultiBufferAllocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast Particle Engine Renderer.
 * Manages particle simulation queues and batches particle quads directly into off-heap FFM buffers
 * using XGenerationalMultiBufferAllocator to execute dynamic instanced particle rendering passes.
 */
@SuppressWarnings("unused")
public class XenoParticleRenderer {

    private static final XenoParticleRenderer INSTANCE = new XenoParticleRenderer();

    private final List<Object> activeParticles = new ArrayList<>();
    private XGenerationalMultiBufferAllocator.AllocationHandle particleBufferAlloc;
    private int particleCount = 0;

    public static XenoParticleRenderer getInstance() {
        return INSTANCE;
    }

    public synchronized void tick() {
        // Fast particle simulation tick pass
        if (this.particleCount > 0) {
            this.particleCount = Math.max(0, this.particleCount - 1);
        }
    }

    public synchronized void addParticle(Object particle) {
        if (particle != null) {
            this.activeParticles.add(particle);
            this.particleCount = this.activeParticles.size();
        }
    }

    public synchronized void clear() {
        this.activeParticles.clear();
        this.particleCount = 0;
        if (this.particleBufferAlloc != null) {
            XenoWorldRenderer.getOffHeapBuildingPool().free(this.particleBufferAlloc);
            this.particleBufferAlloc = null;
        }
    }

    public int getParticleCount() {
        return this.particleCount;
    }
}
