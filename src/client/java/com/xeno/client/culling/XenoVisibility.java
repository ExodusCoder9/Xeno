package com.xeno.client.culling;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.jspecify.annotations.Nullable;

public final class XenoVisibility {
    public static final byte SKIP = 0;
    public static final byte COMPILE = 1;
    public static final byte SKIP_EMPTY = 2;

    private static volatile @Nullable Long2ByteOpenHashMap sectionVisibility;
    private static volatile @Nullable Long2BooleanOpenHashMap opaqueSections;

    public static void publish(Long2ByteOpenHashMap visibility, Long2BooleanOpenHashMap opaque) {
        sectionVisibility = visibility;
        opaqueSections = opaque;
    }

    public static boolean hasVisibilityData() {
        return sectionVisibility != null;
    }

    @SuppressWarnings("unused")
    public static void invalidate() {
        sectionVisibility = null;
        opaqueSections = null;
    }

    public static boolean isOccluded(long sectionNode) {
        Long2ByteOpenHashMap map = sectionVisibility;
        return map == null || map.getOrDefault(sectionNode, SKIP) == SKIP;
    }

    @SuppressWarnings("unused")
    public static boolean shouldCompile(long sectionNode) {
        Long2ByteOpenHashMap map = sectionVisibility;
        if (map == null) return true;
        return map.getOrDefault(sectionNode, SKIP) == COMPILE;
    }

    @SuppressWarnings("unused")
    public static boolean isFullyOpaque(long sectionNode) {
        Long2BooleanOpenHashMap map = opaqueSections;
        return map != null && map.getOrDefault(sectionNode, false);
    }
}
