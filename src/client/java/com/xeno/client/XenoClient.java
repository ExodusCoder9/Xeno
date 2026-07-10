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

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Xeno] Async occlusion culling system loaded");
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
