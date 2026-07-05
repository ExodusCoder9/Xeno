package com.xeno.client.mixin;

import com.xeno.client.culling.XenoOcclusionGraph;
import com.xeno.client.renderer.XenoMdiRenderer;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelRenderer.class)
@SuppressWarnings({"unused"})
public abstract class LevelRendererMixin {
    @Unique
    private static final ThreadLocal<EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>>> DRAW_GROUPS_POOL =
        ThreadLocal.withInitial(() -> {
            EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> map = new EnumMap<>(ChunkSectionLayer.class);
            for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                map.put(layer, new Int2ObjectOpenHashMap<>());
            }
            return map;
        });

    @Unique
    private static final ThreadLocal<ObjectArrayList<DynamicUniforms.ChunkSectionInfo>> SECTION_INFOS_POOL =
        ThreadLocal.withInitial(ObjectArrayList::new);

    @Shadow @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow @Final
    private TextureManager textureManager;

    @Shadow
    private @Nullable SectionRenderDispatcher sectionRenderDispatcher;

    @Shadow
    public abstract SectionOcclusionGraph sectionOcclusionGraph();

    /**
     * @author Antigravity
     * @reason Overwrite prepareChunkRenders to consume pre-split per-layer lists from the background
     *         culling thread directly. This eliminates the per-section × per-layer iteration and filtering
     *         from the main render thread.
     */
    @Overwrite
    @SuppressWarnings("deprecation")
    public ChunkSectionsToRender prepareChunkRenders(final Matrix4fc modelViewMatrix) {
        int largestIndexCount = 0;

        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups = DRAW_GROUPS_POOL.get();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            for (List<RenderPass.Draw<GpuBufferSlice[]>> list : drawGroups.get(layer).values()) {
                list.clear();
            }
        }

        ObjectArrayList<DynamicUniforms.ChunkSectionInfo> sectionInfos = SECTION_INFOS_POOL.get();
        sectionInfos.clear();
        GpuTextureView blockAtlas = this.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        int textureAtlasWidth = blockAtlas.getWidth(0);
        int textureAtlasHeight = blockAtlas.getHeight(0);

        if (this.sectionRenderDispatcher != null) {
            this.sectionRenderDispatcher.lock();
            try {
                XenoOcclusionGraph xenoGraph = (XenoOcclusionGraph) this.sectionOcclusionGraph();
                if (xenoGraph != null) {
                    List<SectionRenderDispatcher.RenderSection> solidList = xenoGraph.xeno$getSolidSections();
                    List<SectionRenderDispatcher.RenderSection> cutoutList = xenoGraph.xeno$getCutoutSections();
                    List<SectionRenderDispatcher.RenderSection> translucentList = xenoGraph.xeno$getTranslucentSections();

                    java.util.IdentityHashMap<SectionRenderDispatcher.RenderSection, Integer> sectionUboMap = new java.util.IdentityHashMap<>();
                    long now = Util.getMillis();

                    for (SectionRenderDispatcher.RenderSection section : solidList) {
                        if (!sectionUboMap.containsKey(section)) {
                            sectionUboMap.put(section, sectionInfos.size());
                            BlockPos renderOffset = section.getRenderOrigin();
                            sectionInfos.add(
                                new DynamicUniforms.ChunkSectionInfo(
                                    modelViewMatrix,
                                    renderOffset.getX(),
                                    renderOffset.getY(),
                                    renderOffset.getZ(),
                                    section.getVisibility(now),
                                    textureAtlasWidth,
                                    textureAtlasHeight
                                )
                            );
                        }
                    }
                    for (SectionRenderDispatcher.RenderSection section : cutoutList) {
                        if (!sectionUboMap.containsKey(section)) {
                            sectionUboMap.put(section, sectionInfos.size());
                            BlockPos renderOffset = section.getRenderOrigin();
                            sectionInfos.add(
                                new DynamicUniforms.ChunkSectionInfo(
                                    modelViewMatrix,
                                    renderOffset.getX(),
                                    renderOffset.getY(),
                                    renderOffset.getZ(),
                                    section.getVisibility(now),
                                    textureAtlasWidth,
                                    textureAtlasHeight
                                )
                            );
                        }
                    }
                    for (SectionRenderDispatcher.RenderSection section : translucentList) {
                        if (!sectionUboMap.containsKey(section)) {
                            sectionUboMap.put(section, sectionInfos.size());
                            BlockPos renderOffset = section.getRenderOrigin();
                            sectionInfos.add(
                                new DynamicUniforms.ChunkSectionInfo(
                                    modelViewMatrix,
                                    renderOffset.getX(),
                                    renderOffset.getY(),
                                    renderOffset.getZ(),
                                    section.getVisibility(now),
                                    textureAtlasWidth,
                                    textureAtlasHeight
                                )
                            );
                        }
                    }

                    for (int i = 0; i < solidList.size(); i++) {
                        SectionRenderDispatcher.RenderSection section = solidList.get(i);
                        SectionMesh sectionMesh = section.getSectionMesh();
                        ChunkSectionLayer layer = ChunkSectionLayer.SOLID;
                        SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                        SectionRenderDispatcher.RenderSectionBufferSlice slice = this.sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);

                        if (slice != null && draw != null && (!draw.hasCustomIndexBuffer() || slice.indexBuffer() != null)) {
                            int uboIndex = sectionUboMap.get(section);

                            int combinedHash = 173;
                            VertexFormat vertexFormat = layer.pipeline().getVertexFormatBinding(0);
                            GpuBuffer vertexBuffer = slice.vertexBuffer();
                            combinedHash = 31 * combinedHash + vertexBuffer.hashCode();

                            int firstIndex = 0;
                            GpuBuffer indexBuffer;
                            IndexType indexType;

                            if (!draw.hasCustomIndexBuffer()) {
                                if (draw.indexCount() > largestIndexCount) {
                                    largestIndexCount = draw.indexCount();
                                }
                                indexBuffer = null;
                                indexType = null;
                            } else {
                                indexBuffer = slice.indexBuffer();
                                indexType = draw.indexType();
                                combinedHash = 31 * combinedHash + indexBuffer.hashCode();
                                combinedHash = 31 * combinedHash + indexType.hashCode();
                                firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                            }

                            int baseVertex = (int) (slice.vertexBufferOffset() / Objects.requireNonNull(vertexFormat).getVertexSize());

                            List<RenderPass.Draw<GpuBufferSlice[]>> draws = drawGroups.get(layer)
                                .computeIfAbsent(combinedHash, k -> new ArrayList<>());

                            draws.add(new RenderPass.Draw<>(
                                uboIndex, vertexBuffer, indexBuffer, indexType, firstIndex, draw.indexCount(), baseVertex
                            ));
                        }
                    }

                    for (int i = 0; i < cutoutList.size(); i++) {
                        SectionRenderDispatcher.RenderSection section = cutoutList.get(i);
                        SectionMesh sectionMesh = section.getSectionMesh();
                        ChunkSectionLayer layer = ChunkSectionLayer.CUTOUT;
                        SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                        SectionRenderDispatcher.RenderSectionBufferSlice slice = this.sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);

                        if (slice != null && draw != null && (!draw.hasCustomIndexBuffer() || slice.indexBuffer() != null)) {
                            int uboIndex = sectionUboMap.get(section);

                            int combinedHash = 173;
                            VertexFormat vertexFormat = layer.pipeline().getVertexFormatBinding(0);
                            GpuBuffer vertexBuffer = slice.vertexBuffer();
                            combinedHash = 31 * combinedHash + vertexBuffer.hashCode();

                            int firstIndex = 0;
                            GpuBuffer indexBuffer;
                            IndexType indexType;

                            if (!draw.hasCustomIndexBuffer()) {
                                if (draw.indexCount() > largestIndexCount) {
                                    largestIndexCount = draw.indexCount();
                                }
                                indexBuffer = null;
                                indexType = null;
                            } else {
                                indexBuffer = slice.indexBuffer();
                                indexType = draw.indexType();
                                combinedHash = 31 * combinedHash + indexBuffer.hashCode();
                                combinedHash = 31 * combinedHash + indexType.hashCode();
                                firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                            }

                            int baseVertex = (int) (slice.vertexBufferOffset() / Objects.requireNonNull(vertexFormat).getVertexSize());

                            List<RenderPass.Draw<GpuBufferSlice[]>> draws = drawGroups.get(layer)
                                .computeIfAbsent(combinedHash, k -> new ArrayList<>());

                            draws.add(new RenderPass.Draw<>(
                                uboIndex, vertexBuffer, indexBuffer, indexType, firstIndex, draw.indexCount(), baseVertex
                            ));
                        }
                    }

                    for (int i = 0; i < translucentList.size(); i++) {
                        SectionRenderDispatcher.RenderSection section = translucentList.get(i);
                        SectionMesh sectionMesh = section.getSectionMesh();
                        ChunkSectionLayer layer = ChunkSectionLayer.TRANSLUCENT;
                        SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                        SectionRenderDispatcher.RenderSectionBufferSlice slice = this.sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);

                        if (slice != null && draw != null && (!draw.hasCustomIndexBuffer() || slice.indexBuffer() != null)) {
                            int uboIndex = sectionUboMap.get(section);

                            VertexFormat vertexFormat = layer.pipeline().getVertexFormatBinding(0);
                            GpuBuffer vertexBuffer = slice.vertexBuffer();

                            int firstIndex = 0;
                            GpuBuffer indexBuffer;
                            IndexType indexType;

                            if (!draw.hasCustomIndexBuffer()) {
                                if (draw.indexCount() > largestIndexCount) {
                                    largestIndexCount = draw.indexCount();
                                }
                                indexBuffer = null;
                                indexType = null;
                            } else {
                                indexBuffer = slice.indexBuffer();
                                indexType = draw.indexType();
                                firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                            }

                            int baseVertex = (int) (slice.vertexBufferOffset() / Objects.requireNonNull(vertexFormat).getVertexSize());

                            List<RenderPass.Draw<GpuBufferSlice[]>> draws = drawGroups.get(layer)
                                .computeIfAbsent(173, k -> new ArrayList<>());

                            draws.add(new RenderPass.Draw<>(
                                uboIndex, vertexBuffer, indexBuffer, indexType, firstIndex, draw.indexCount(), baseVertex
                            ));
                        }
                    }
                }
            } finally {
                this.sectionRenderDispatcher.unlock();
            }
        }

        GpuBufferSlice[] chunkSectionInfos = RenderSystem.getDynamicUniforms().writeChunkSections(
            sectionInfos.toArray(new DynamicUniforms.ChunkSectionInfo[0])
        );

        XenoMdiRenderer.CURRENT_SECTION_INFOS.set(sectionInfos);
        return new ChunkSectionsToRender(blockAtlas, drawGroups, largestIndexCount, chunkSectionInfos);
    }
}
