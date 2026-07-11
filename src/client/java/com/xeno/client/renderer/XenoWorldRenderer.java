package com.xeno.client.renderer;

import com.mojang.logging.LogUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Central hub for Xeno's render pipeline optimizations.
 * <p>
 * Holds references to vanilla infrastructure captured from {@link net.minecraft.client.renderer.LevelRenderer}
 * and provides statistics for the optimization systems. All mixin hooks in
 * {@link com.xeno.client.mixin.LevelRendererMixin} delegate here.
 *
 * @author ExodusCoder9
 */
public final class XenoWorldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable XenoWorldRenderer instance;

    private int compileSectionsSkipped;
    private int compileSectionsProcessed;
    private int framesSinceInit;

    public XenoWorldRenderer() {
        LOGGER.info("[Xeno] XenoWorldRenderer initialized");
    }

    public static void setInstance(@Nullable XenoWorldRenderer renderer) {
        instance = renderer;
    }

    public static @Nullable XenoWorldRenderer getInstance() {
        return instance;
    }

    public void onCompileSectionsFrame(int skipped, int processed) {
        this.compileSectionsSkipped = skipped;
        this.compileSectionsProcessed = processed;
        this.framesSinceInit++;
    }

    public int getCompileSectionsSkipped() {
        return this.compileSectionsSkipped;
    }

    public int getCompileSectionsProcessed() {
        return this.compileSectionsProcessed;
    }

    public int getFramesSinceInit() {
        return this.framesSinceInit;
    }

    public void destroy() {
        this.compileSectionsSkipped = 0;
        this.compileSectionsProcessed = 0;
        this.framesSinceInit = 0;
        LOGGER.info("[Xeno] XenoWorldRenderer destroyed");
    }
}
