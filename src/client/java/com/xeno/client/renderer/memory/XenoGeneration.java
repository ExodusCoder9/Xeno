package com.xeno.client.renderer.memory;

/**
 * Generational tiers for allocation lifecycle tracking.
 */
public enum XenoGeneration {
    YOUNG,
    SURVIVOR,
    OLD
}
