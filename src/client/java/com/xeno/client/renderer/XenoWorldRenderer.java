package com.xeno.client.renderer;

import com.mojang.logging.LogUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class XenoWorldRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable XenoWorldRenderer instance;

    private int compileSectionsSkipped;
    private int compileSectionsProcessed;
    private int framesSinceInit;
    private boolean active;

    public XenoWorldRenderer() {
        this.active = true;
        LOGGER.info("[Xeno] World renderer initialized");
    }

    public static void setInstance(@Nullable XenoWorldRenderer renderer) {
        instance = renderer;
    }

    public static @Nullable XenoWorldRenderer getInstance() {
        return instance;
    }

    public void reload() {
        LOGGER.info("[Xeno] World renderer reloading");
        this.compileSectionsSkipped = 0;
        this.compileSectionsProcessed = 0;
        this.framesSinceInit = 0;
        this.active = true;
    }

    public void onCompileSectionsFrame(int skipped, int processed) {
        this.compileSectionsSkipped = skipped;
        this.compileSectionsProcessed = processed;
        this.framesSinceInit++;
    }

    public boolean isActive() {
        return this.active;
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
        this.active = false;
        this.compileSectionsSkipped = 0;
        this.compileSectionsProcessed = 0;
        this.framesSinceInit = 0;
        LOGGER.info("[Xeno] World renderer destroyed");
    }
}
