package com.xeno.client;

import com.mojang.logging.LogUtils;
import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingThread;
import com.xeno.client.renderer.XenoWorldRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class XenoClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable CullingThread cullingThread;
    private static @Nullable ViewArea viewArea;

    private static float cameraYaw;
    private static float cameraPitch;
    private static @Nullable Vec3 cameraPos;
    private static @Nullable LevelExtractor levelExtractor;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Xeno] Async occlusion culling system loaded (Optimized Mesher Active)");
    }

    public static void setCameraState(float yaw, float pitch, Vec3 pos) {
        cameraYaw = yaw;
        cameraPitch = pitch;
        cameraPos = pos;
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    public static @Nullable Vec3 getCameraPos() {
        return cameraPos;
    }

    public static void setLevelExtractor(@Nullable LevelExtractor extractor) {
        levelExtractor = extractor;
    }

    public static @Nullable LevelExtractor getLevelExtractor() {
        return levelExtractor;
    }

    public static void setCullingThread(@Nullable CullingThread thread) {
        cullingThread = thread;
    }

    public static @Nullable CullingThread getCullingThread() {
        return cullingThread;
    }

    public static @Nullable CullingOutput getLatestCullingOutput() {
        CullingThread thread = cullingThread;
        return thread != null ? thread.getLatestOutput() : null;
    }

    public static void setViewArea(@Nullable ViewArea area) {
        viewArea = area;
    }

    public static @Nullable ViewArea getViewArea() {
        return viewArea;
    }

    public static @Nullable XenoWorldRenderer getXenoWorldRenderer() {
        return XenoWorldRenderer.getInstance();
    }

    public static void onWorldRendererCreated() {
        LOGGER.info("[Xeno] XenoWorldRenderer fully initialized");
    }

    public static void onWorldRendererDestroyed() {
        LOGGER.info("[Xeno] XenoWorldRenderer destroyed");
    }
}