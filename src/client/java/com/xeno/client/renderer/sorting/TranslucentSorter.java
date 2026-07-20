package com.xeno.client.renderer.sorting;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.xeno.client.renderer.memory.MemoryIntrinsics;
import com.xeno.client.renderer.memory.XGenerationalMultiBufferAllocator;
import net.minecraft.client.Minecraft;
import com.xeno.client.XenoClient;
import com.xeno.client.renderer.util.XenoMeshExtension;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

public final class TranslucentSorter {

    private TranslucentSorter() {
        // Prevent instantiation
    }

    public static void resort(
            CompiledSectionMesh compiled,
            XenoMeshExtension ext,
            long sectionNode,
            BlockPos origin,
            XGenerationalMultiBufferAllocator.AllocationHandle indexAlloc
    ) {
        float[] centers = ext.xeno$getTranslucentQuadCenters();
        int quadCount = ext.xeno$getTranslucentQuadCount();

        if (centers != null && quadCount > 0 && indexAlloc != null && indexAlloc.valid) {
            CompletableFuture.runAsync(() -> {
                Vec3 cameraPos = XenoClient.getCameraPos();
                double cx = cameraPos != null ? cameraPos.x - origin.getX() : 0;
                double cy = cameraPos != null ? cameraPos.y - origin.getY() : 0;
                double cz = cameraPos != null ? cameraPos.z - origin.getZ() : 0;

                float dx = (float) cx;
                float dy = (float) cy;
                float dz = (float) cz;

                float[] depths = new float[quadCount];
                int[] indices = new int[quadCount];
                for (int i = 0; i < quadCount; i++) {
                    float qx = centers[i * 3];
                    float qy = centers[i * 3 + 1];
                    float qz = centers[i * 3 + 2];
                    depths[i] = qx * dx + qy * dy + qz * dz;
                    indices[i] = i;
                }

                sortIndices(depths, indices, 0, quadCount - 1);

                ByteBuffer indexBuf = ByteBuffer.allocateDirect(quadCount * 6 * 2);
                indexBuf.order(ByteOrder.nativeOrder());
                for (int i = 0; i < quadCount; i++) {
                    int quadIndex = indices[i];
                    int v0 = quadIndex * 4;
                    int v1 = v0 + 1;
                    int v2 = v0 + 2;
                    int v3 = v0 + 3;
                    indexBuf.putShort((short) v0);
                    indexBuf.putShort((short) v1);
                    indexBuf.putShort((short) v2);
                    indexBuf.putShort((short) v0);
                    indexBuf.putShort((short) v2);
                    indexBuf.putShort((short) v3);
                }
                indexBuf.flip();

                Minecraft.getInstance().execute(() -> {
                    if (indexAlloc.getBuffer() != null && !indexAlloc.getBuffer().isClosed()) {
                        try (GpuBufferSlice.MappedView view = indexAlloc.getBuffer().map(indexAlloc.offset, (long) quadCount * 6 * 2, false, true)) {
                            MemoryIntrinsics.copy(indexBuf, view.data(), (long) quadCount * 6 * 2);
                        }

                        TranslucencyPointOfView pointOfView = TranslucencyPointOfView.of(cameraPos != null ? cameraPos : Vec3.ZERO, sectionNode);
                        compiled.setTranslucencyPointOfView(pointOfView);
                    }
                });
            }, Util.backgroundExecutor());
        }
    }

    private static void sortIndices(float[] depths, int[] indices, int left, int right) {
        if (left >= right) return;
        int p = partition(depths, indices, left, right);
        sortIndices(depths, indices, left, p - 1);
        sortIndices(depths, indices, p + 1, right);
    }

    private static int partition(float[] depths, int[] indices, int left, int right) {
        float pivot = depths[indices[right]];
        int i = left - 1;
        for (int j = left; j < right; j++) {
            if (depths[indices[j]] > pivot) {
                i++;
                int temp = indices[i];
                indices[i] = indices[j];
                indices[j] = temp;
            }
        }
        int temp = indices[i + 1];
        indices[i + 1] = indices[right];
        indices[right] = temp;
        return i + 1;
    }
}
