package com.xeno.client;

import com.xeno.client.culling.CullingOutput;
import com.xeno.client.culling.CullingThread;
import com.xeno.client.meshing.FrustumFaceCulling;
import com.xeno.client.meshing.SectionFaceData;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class XenoClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable CullingThread cullingThread;
    private static final SectionFaceData sectionFaceData = new SectionFaceData(4096);
    private static final FrustumFaceCulling frustumFaceCulling = new FrustumFaceCulling();

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Xeno] Async occlusion culling system loaded");
    }

    public static void setCullingThread(CullingThread thread) {
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
}
