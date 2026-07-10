package com.xeno.client;

import com.mojang.logging.LogUtils;
import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingThread;
import com.xeno.client.meshing.FrustumFaceCulling;
import com.xeno.client.meshing.SectionFaceData;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class XenoClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable CullingThread cullingThread;
    private static final SectionFaceData sectionFaceData = new SectionFaceData(4096);
    private static final FrustumFaceCulling frustumFaceCulling = new FrustumFaceCulling();
    private static @Nullable ViewArea viewArea;

    public static final ThreadLocal<Long> xenoCurrentSectionNode = new ThreadLocal<>();
    public static final ThreadLocal<int[]> xenoPerDirCounts = ThreadLocal.withInitial(() -> new int[6]);
    public static final ThreadLocal<int[]> xenoTotalVertices = ThreadLocal.withInitial(() -> new int[1]);
    public static final ThreadLocal<Boolean> xenoShouldCull = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<float[]> xenoCullDir = ThreadLocal.withInitial(() -> new float[3]);

    private static float cameraYaw;
    private static float cameraPitch;
    private static @Nullable Vec3 cameraPos;
    private static @Nullable LevelExtractor levelExtractor;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Xeno] Async occlusion culling system loaded");
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

    public static SectionFaceData getSectionFaceData() {
        return sectionFaceData;
    }

    public static FrustumFaceCulling getFrustumFaceCulling() {
        return frustumFaceCulling;
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

    public static @Nullable Long xenoGetCurrentSectionNode() {
        return xenoCurrentSectionNode.get();
    }

    public static void xenoSetCurrentSectionNode(Long sectionNode) {
        xenoCurrentSectionNode.set(sectionNode);
    }

    public static void xenoClearCurrentSectionNode() {
        xenoCurrentSectionNode.remove();
        xenoPerDirCounts.remove();
        xenoTotalVertices.remove();
        xenoShouldCull.remove();
        xenoCullDir.remove();
    }

    public static float xenoDotProduct(int directionOrdinal, float dx, float dy, float dz) {
        return switch (directionOrdinal) {
            case 0 -> dy;
            case 1 -> -dy;
            case 2 -> dz;
            case 3 -> -dz;
            case 4 -> dx;
            case 5 -> -dx;
            default -> 0.0f;
        };
    }
}