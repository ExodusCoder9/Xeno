package com.xeno.client;

import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingThread;
import com.xeno.client.meshing.FrustumFaceCulling;
import com.xeno.client.meshing.SectionFaceData;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.GpuFormat;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class XenoClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable CullingThread cullingThread;
    private static final SectionFaceData sectionFaceData = new SectionFaceData(4096);
    private static final FrustumFaceCulling frustumFaceCulling = new FrustumFaceCulling();
    private static @Nullable ViewArea viewArea;

    // Thread-local compiler state for the current section being built
    public static final ThreadLocal<Long> xenoCurrentSectionNode = new ThreadLocal<>();
    public static final ThreadLocal<int[]> xenoPerDirCounts = ThreadLocal.withInitial(() -> new int[6]);
    public static final ThreadLocal<int[]> xenoTotalVertices = ThreadLocal.withInitial(() -> new int[1]);
    public static final ThreadLocal<Boolean> xenoShouldCull = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<float[]> xenoCullDir = ThreadLocal.withInitial(() -> new float[3]);

    // Camera State Tracking
    private static float cameraYaw;
    private static float cameraPitch;
    private static @Nullable Vec3 cameraPos;

    // LevelExtractor Tracking
    private static @Nullable LevelExtractor levelExtractor;

    // Custom 16-byte Vertex Format
    public static final VertexFormat COMPRESSED_BLOCK_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB16_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG16_FLOAT)
            .addAttribute("UV2", GpuFormat.RG8_UINT)
            .build();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Xeno] Async occlusion culling system loaded");
    }

    @SuppressWarnings("unused")
    public static void setCameraState(float yaw, float pitch, Vec3 pos) {
        cameraYaw = yaw;
        cameraPitch = pitch;
        cameraPos = pos;
    }

    @SuppressWarnings("unused")
    public static float getCameraYaw() {
        return cameraYaw;
    }

    @SuppressWarnings("unused")
    public static float getCameraPitch() {
        return cameraPitch;
    }

    @SuppressWarnings("unused")
    public static @Nullable Vec3 getCameraPos() {
        return cameraPos;
    }

    @SuppressWarnings("unused")
    public static void setLevelExtractor(@Nullable LevelExtractor extractor) {
        levelExtractor = extractor;
    }

    @SuppressWarnings("unused")
    public static @Nullable LevelExtractor getLevelExtractor() {
        return levelExtractor;
    }

    public static void setCullingThread(@Nullable CullingThread thread) {
        cullingThread = thread;
    }

    @SuppressWarnings("unused")
    public static @Nullable CullingThread getCullingThread() {
        return cullingThread;
    }

    public static SectionFaceData getSectionFaceData() {
        return sectionFaceData;
    }

    @SuppressWarnings("unused")
    public static FrustumFaceCulling getFrustumFaceCulling() {
        return frustumFaceCulling;
    }

    @SuppressWarnings("unused")
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
}
