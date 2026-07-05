package com.xeno.client.renderer;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public interface XenoDispatcherAccess {
    Map<ChunkSectionLayer, XenoMeshArena> xeno$getArenas();
    ConcurrentLinkedQueue<PendingUpload> xeno$getPendingUploads();
    ConcurrentLinkedQueue<Runnable> xeno$getRenderThreadCallbacks();
}
