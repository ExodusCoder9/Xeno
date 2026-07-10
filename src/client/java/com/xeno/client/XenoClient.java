package com.xeno.client;

import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingThread;
import com.xeno.client.meshing.FrustumFaceCulling;
import com.xeno.client.meshing.SectionFaceData;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.ViewArea;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class XenoClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable CullingThread cullingThread;
    private static final SectionFaceData sectionFaceData = new SectionFaceData(4096);
    private static final FrustumFaceCulling frustumFaceCulling = new FrustumFaceCulling();
    private static @Nullable ViewArea viewArea;
    private static final ThreadLocal<Long> xenoCurrentSectionNode = new ThreadLocal<>();

    // Camera State Tracking
    private static float cameraYaw;
    private static float cameraPitch;
    private static @Nullable net.minecraft.world.phys.Vec3 cameraPos;

    // LevelExtractor Tracking
    private static @Nullable net.minecraft.client.renderer.extract.LevelExtractor levelExtractor;

    // Custom 16-byte Vertex Format
    public static final com.mojang.blaze3d.vertex.VertexFormat COMPRESSED_BLOCK_FORMAT = com.mojang.blaze3d.vertex.VertexFormat.builder(0)
            .addAttribute("Position", com.mojang.blaze3d.GpuFormat.RGB16_FLOAT)
            .addAttribute("Color", com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", com.mojang.blaze3d.GpuFormat.RG16_FLOAT)
            .addAttribute("UV2", com.mojang.blaze3d.GpuFormat.RG8_UINT)
            .build();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Xeno] Async occlusion culling system loaded");
    }

    public static void setCameraState(float yaw, float pitch, net.minecraft.world.phys.Vec3 pos) {
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

    public static @Nullable net.minecraft.world.phys.Vec3 getCameraPos() {
        return cameraPos;
    }

    public static void setLevelExtractor(@Nullable net.minecraft.client.renderer.extract.LevelExtractor extractor) {
        levelExtractor = extractor;
    }

    public static @Nullable net.minecraft.client.renderer.extract.LevelExtractor getLevelExtractor() {
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

    @SuppressWarnings("unused")
    public static Long xenoGetCurrentSectionNode() {
        return xenoCurrentSectionNode.get();
    }

    public static void xenoSetCurrentSectionNode(Long sectionNode) {
        xenoCurrentSectionNode.set(sectionNode);
    }

    public static void xenoClearCurrentSectionNode() {
        xenoCurrentSectionNode.remove();
    }
}
