package com.xeno.client.renderer.draw;

import com.xeno.client.renderer.util.XenoMeshExtension;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Concrete implementation of IXenoDrawListManager.
 * It compiles visible sections into grouped draw calls, utilizing cached draws and binders.
 */
public class XenoDrawListManager implements IXenoDrawListManager {

    private static final ThreadLocal<EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>>> DRAW_GROUPS = ThreadLocal.withInitial(() -> {
        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> map = new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            map.put(layer, new Int2ObjectOpenHashMap<>());
        }
        return map;
    });

    private static final ThreadLocal<List<DynamicUniforms.ChunkSectionInfo>> SECTION_INFOS = ThreadLocal.withInitial(ArrayList::new);

    private static void clearGroups(EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> groups) {
        for (Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> map : groups.values()) {
            for (List<RenderPass.Draw<GpuBufferSlice[]>> list : map.values()) {
                list.clear();
            }
            map.clear();
        }
    }

    @Override
    public ChunkSectionsToRender prepareChunkRenders(
            List<SectionRenderDispatcher.RenderSection> visibleSections,
            SectionRenderDispatcher sectionRenderDispatcher,
            TextureManager textureManager,
            Matrix4fc modelViewMatrix
    ) {
        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups = DRAW_GROUPS.get();
        clearGroups(drawGroups);

        List<DynamicUniforms.ChunkSectionInfo> sectionInfos = SECTION_INFOS.get();
        sectionInfos.clear();

        GpuTextureView blockAtlas = textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        int textureAtlasWidth = blockAtlas.getWidth(0);
        int textureAtlasHeight = blockAtlas.getHeight(0);

        int largestIndexCount = 0;

        if (sectionRenderDispatcher != null) {
            sectionRenderDispatcher.lock();
            try {
                long now = Util.getMillis();
                int size = visibleSections.size();

                for (int s = 0; s < size; s++) {
                    SectionRenderDispatcher.RenderSection section = visibleSections.get(s);
                    if (section == null) continue;

                    SectionMesh sectionMesh = section.getSectionMesh();
                    if (!(sectionMesh instanceof XenoMeshExtension ext)) continue;

                    BlockPos renderOffset = section.getRenderOrigin();
                    int uboIndex = -1;

                    for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                        SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                        if (draw == null) continue;

                        RenderPass.Draw<GpuBufferSlice[]> cachedDraw = ext.xeno$getCachedDraw(layer);
                        if (cachedDraw == null) continue;

                        if (uboIndex == -1) {
                            uboIndex = sectionInfos.size();
                            sectionInfos.add(
                                    new DynamicUniforms.ChunkSectionInfo(
                                            new Matrix4f(modelViewMatrix),
                                            renderOffset.getX(),
                                            renderOffset.getY(),
                                            renderOffset.getZ(),
                                            section.getVisibility(now),
                                            textureAtlasWidth,
                                            textureAtlasHeight
                                    )
                            );
                        }

                        // Update the dynamic UBO index for the cached draw call
                        XenoUniformBinder binder = ext.xeno$getUniformBinder(layer);
                        if (binder != null) {
                            binder.uboIndex = uboIndex;
                        }

                        int combinedHash = 173;
                        GpuBuffer vertexBuffer = cachedDraw.vertexBuffer();
                        if (layer != ChunkSectionLayer.TRANSLUCENT) {
                            combinedHash = 31 * combinedHash + vertexBuffer.hashCode();
                        }

                        if (!draw.hasCustomIndexBuffer()) {
                            if (draw.indexCount() > largestIndexCount) {
                                largestIndexCount = draw.indexCount();
                            }
                        } else {
                            GpuBuffer indexBuffer = cachedDraw.indexBuffer();
                            com.mojang.blaze3d.IndexType indexType = cachedDraw.indexType();
                            if (indexBuffer != null && indexType != null && layer != ChunkSectionLayer.TRANSLUCENT) {
                                combinedHash = 31 * combinedHash + indexBuffer.hashCode();
                                combinedHash = 31 * combinedHash + indexType.hashCode();
                            }
                        }

                        Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> map = drawGroups.get(layer);
                        List<RenderPass.Draw<GpuBufferSlice[]>> list = map.get(combinedHash);
                        if (list == null) {
                            list = new ArrayList<>();
                            map.put(combinedHash, list);
                        }
                        list.add(cachedDraw);
                    }
                }
            } finally {
                sectionRenderDispatcher.unlock();
            }
        }

        GpuBufferSlice[] chunkSectionInfos = RenderSystem.getDynamicUniforms().writeChunkSections(
                sectionInfos.toArray(new DynamicUniforms.ChunkSectionInfo[0])
        );

        // Copy drawGroups structure for the record
        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> finalGroups = new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> originalMap = drawGroups.get(layer);
            Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> copiedMap = new Int2ObjectOpenHashMap<>();
            for (int key : originalMap.keySet()) {
                copiedMap.put(key, new ArrayList<>(originalMap.get(key)));
            }
            finalGroups.put(layer, copiedMap);
        }

        return new ChunkSectionsToRender(blockAtlas, finalGroups, largestIndexCount, chunkSectionInfos);
    }
}
