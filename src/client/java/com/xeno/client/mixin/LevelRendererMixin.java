package com.xeno.client.mixin;

import com.xeno.client.renderer.XenoDrawCache;
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
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelRenderer.class)
@SuppressWarnings({"unused"})
public abstract class LevelRendererMixin {
    @Shadow @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow @Final
    private TextureManager textureManager;

    @Shadow
    private @Nullable SectionRenderDispatcher sectionRenderDispatcher;

    /**
     * @author Antigravity
     * @reason Overwrite prepareChunkRenders to retrieve pre-allocated, flyweight RenderPass.Draw records from XenoDrawCache.
     *         This completely eliminates garbage collection allocations of Draw and lambda objects every frame.
     */
    @Overwrite
    @SuppressWarnings("deprecation")
    public ChunkSectionsToRender prepareChunkRenders(final Matrix4fc modelViewMatrix) {
        ObjectListIterator<SectionRenderDispatcher.RenderSection> iterator = this.visibleSections.listIterator(0);
        EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups = new EnumMap<>(ChunkSectionLayer.class);
        int largestIndexCount = 0;

        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            drawGroups.put(layer, new Int2ObjectOpenHashMap<>());
        }

        List<DynamicUniforms.ChunkSectionInfo> sectionInfos = new ArrayList<>();
        GpuTextureView blockAtlas = this.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        int textureAtlasWidth = blockAtlas.getWidth(0);
        int textureAtlasHeight = blockAtlas.getHeight(0);

        if (this.sectionRenderDispatcher != null) {
            this.sectionRenderDispatcher.lock();
            try {
                while (iterator.hasNext()) {
                    SectionRenderDispatcher.RenderSection section = iterator.next();
                    SectionMesh sectionMesh = section.getSectionMesh();
                    BlockPos renderOffset = section.getRenderOrigin();
                    long now = Util.getMillis();
                    int uboIndex = -1;

                    for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                        SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                        SectionRenderDispatcher.RenderSectionBufferSlice slice = this.sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);

                        if (slice != null && draw != null && (!draw.hasCustomIndexBuffer() || slice.indexBuffer() != null)) {
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

                            int combinedHash = 173;
                            VertexFormat vertexFormat = layer.pipeline().getVertexFormatBinding(0);
                            GpuBuffer vertexBuffer = slice.vertexBuffer();

                            if (layer != ChunkSectionLayer.TRANSLUCENT) {
                                combinedHash = 31 * combinedHash + vertexBuffer.hashCode();
                            }

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

                                if (layer != ChunkSectionLayer.TRANSLUCENT) {
                                    combinedHash = 31 * combinedHash + indexBuffer.hashCode();
                                    combinedHash = 31 * combinedHash + indexType.hashCode();
                                }
                                firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                            }

                            int finalUboIndex = uboIndex;
                            int baseVertex = (int) (slice.vertexBufferOffset() / Objects.requireNonNull(vertexFormat).getVertexSize());

                            List<RenderPass.Draw<GpuBufferSlice[]>> draws = drawGroups.get(layer)
                                .computeIfAbsent(combinedHash, k -> new ArrayList<>());

                            // Optimize chunk Draw allocations by fetching/mutating pre-allocated objects from XenoDrawCache
                            RenderPass.Draw<GpuBufferSlice[]> drawObj = XenoDrawCache.getOrCreate(
                                vertexBuffer,
                                indexBuffer,
                                indexType,
                                firstIndex,
                                draw.indexCount(),
                                baseVertex,
                                finalUboIndex
                            );

                            draws.add(drawObj);
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
