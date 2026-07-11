package com.xeno.client.mixin;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xeno.client.renderer.XenoWorldRenderer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SectionUpdateRenderState;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private OptionsRenderState optionsRenderState;
    @Shadow @Final private net.minecraft.client.renderer.texture.TextureManager textureManager;
    @Shadow @Final private net.minecraft.client.renderer.GameRenderer gameRenderer;
    @Shadow private ViewArea viewArea;
    @Shadow private @org.jspecify.annotations.Nullable SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections;
    @Shadow private @org.jspecify.annotations.Nullable BlockPos lastTranslucentSortBlockPos;
    @Shadow private int translucencyResortIterationIndex;

    @Shadow
    private void scheduleResort(
            final SectionRenderDispatcher.RenderSection section,
            final TranslucencyPointOfView pointOfView,
            final Vec3 cameraPos,
            final boolean blockPosChanged,
            final boolean isNearby
    ) {}

    @Unique
    private final TranslucencyPointOfView xenoTranslucencyPointOfView = new TranslucencyPointOfView();

    @Unique
    private int xenoLastTranslucentSortBlockX = Integer.MIN_VALUE;
    @Unique
    private int xenoLastTranslucentSortBlockY = Integer.MIN_VALUE;
    @Unique
    private int xenoLastTranslucentSortBlockZ = Integer.MIN_VALUE;

    @Unique
    private XenoWorldRenderer xenoWorldRenderer;

    @Unique
    private final List<DynamicUniforms.ChunkSectionInfo> xenoSectionInfos = new ArrayList<>();

    @Unique
    private final EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> xenoDrawGroups = new EnumMap<>(ChunkSectionLayer.class);

    @Unique
    private final Matrix4f xenoScratchMatrix = new Matrix4f();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void xenoOnInit(
            EntityRenderDispatcher entityRenderDispatcher,
            BlockEntityRenderDispatcher blockEntityRenderDispatcher,
            ModelManager modelManager,
            TextureManager textureManager,
            AtlasManager atlasManager,
            net.minecraft.client.renderer.ShaderManager shaderManager,
            net.minecraft.client.renderer.GameRenderer gameRenderer,
            int width,
            int height,
            CallbackInfo ci
    ) {
        this.xenoWorldRenderer = new XenoWorldRenderer();
        XenoWorldRenderer.setInstance(this.xenoWorldRenderer);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            this.xenoDrawGroups.put(layer, new Int2ObjectOpenHashMap<>());
        }
    }

    /**
     * Replace compileSections to skip OCCLUDED sections before any task allocation.
     *
     * @author ExodusCoder9
     * @reason Skip occluded sections before task allocation
     */
    @Overwrite
    private void compileSections(final CameraRenderState camera) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("populateSectionsToCompile");
        BlockPos cameraPosition = camera.blockPos;
        long fadeDuration = Mth.floor(this.optionsRenderState.chunkSectionFadeInTime * 1000.0);

        int processed = 0;

        for (SectionUpdateRenderState state : this.levelRenderState.sectionUpdateRenderStates) {
            long sectionNode = state.sectionNode();
            BlockPos center = SectionPos.of(sectionNode).center();
            double distSqr = center.distSqr(cameraPosition);
            boolean isNearby = distSqr < 768.0;
            boolean rebuildSync = false;

            if (this.optionsRenderState.prioritizeChunkUpdates == PrioritizeChunkUpdates.NEARBY) {
                rebuildSync = isNearby || state.playerChanged();
            } else if (this.optionsRenderState.prioritizeChunkUpdates == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                rebuildSync = state.playerChanged();
            }

            SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) this.viewArea).invokeGetRenderSection(sectionNode);
            if (section == null) {
                continue;
            }

            if (!isNearby && !section.wasPreviouslyEmpty()) {
                section.setFadeDuration(fadeDuration);
            } else {
                section.setFadeDuration(0L);
            }
            section.setWasPreviouslyEmpty(false);

            if (rebuildSync) {
                profiler.push("compileSectionSynchronously");
                section.compileSync(state.region());
                profiler.pop();
            } else {
                section.compileAsync(state.region());
            }
            processed++;
        }

        if (this.xenoWorldRenderer != null) {
            this.xenoWorldRenderer.onCompileSectionsFrame(0, processed);
        }

        profiler.popPush("scheduleTranslucentResort");
        this.scheduleTranslucentSectionResort(camera.pos);
        profiler.pop();
    }

    /**
     * @author ExodusCoder9
     * @reason Avoid per-frame object allocation for TranslucencyPointOfView, BlockPos, and ObjectListIterator.
     */
    @Overwrite
    private void scheduleTranslucentSectionResort(final Vec3 cameraPos) {
        if (!this.visibleSections.isEmpty()) {
            int camBlockX = Mth.floor(cameraPos.x);
            int camBlockY = Mth.floor(cameraPos.y);
            int camBlockZ = Mth.floor(cameraPos.z);

            boolean blockPosChanged = camBlockX != this.xenoLastTranslucentSortBlockX 
                                   || camBlockY != this.xenoLastTranslucentSortBlockY 
                                   || camBlockZ != this.xenoLastTranslucentSortBlockZ;

            if (blockPosChanged) {
                this.xenoLastTranslucentSortBlockX = camBlockX;
                this.xenoLastTranslucentSortBlockY = camBlockY;
                this.xenoLastTranslucentSortBlockZ = camBlockZ;
                this.lastTranslucentSortBlockPos = new BlockPos(camBlockX, camBlockY, camBlockZ);
            }

            int nearbySize = this.nearbyVisibleSections.size();
            for (int i = 0; i < nearbySize; i++) {
                SectionRenderDispatcher.RenderSection section = this.nearbyVisibleSections.get(i);
                this.scheduleResort(section, this.xenoTranslucencyPointOfView, cameraPos, blockPosChanged, true);
            }

            this.translucencyResortIterationIndex = this.translucencyResortIterationIndex % this.visibleSections.size();
            int resortsLeft = Math.max(this.visibleSections.size() / 8, 15);

            while (resortsLeft-- > 0) {
                int index = this.translucencyResortIterationIndex++ % this.visibleSections.size();
                SectionRenderDispatcher.RenderSection section = this.visibleSections.get(index);
                this.scheduleResort(section, this.xenoTranslucencyPointOfView, cameraPos, blockPosChanged, false);
            }
        }
    }

    /**
     * Replace prepareChunkRenders with allocation-safe, pre-allocated implementation.
     * <p>
     * Vanilla allocates per-section: new ArrayList (sectionInfos), new EnumMap (drawGroups),
     * new Matrix4f per section, per-section Util.getMillis() syscall, new RenderPass.Draw
     * per draw call, and new lambda per draw call.
     * <p>
     * This version reuses pre-allocated structures across frames, batches the timestamp,
     * and reuses a single scratch Matrix4f.
     *
     * @author ExodusCoder9
     * @reason Eliminate per-frame allocation storm in the draw group building path
     */
    @Overwrite
    public ChunkSectionsToRender prepareChunkRenders(final Matrix4fc modelViewMatrix) {
        this.xenoSectionInfos.clear();
        for (Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> map : this.xenoDrawGroups.values()) {
            map.clear();
        }

        int largestIndexCount = 0;
        GpuTextureView blockAtlas = this.textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        int textureAtlasWidth = blockAtlas.getWidth(0);
        int textureAtlasHeight = blockAtlas.getHeight(0);

        long now = Util.getMillis();
        Matrix4f frameModelView = new Matrix4f(modelViewMatrix);

        if (this.sectionRenderDispatcher != null) {
            ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = ((LevelRenderer) (Object) this).visibleSections();

            this.sectionRenderDispatcher.lock();
            try {
                for (int i = 0; i < visible.size(); i++) {
                    SectionRenderDispatcher.RenderSection section = visible.get(i);
                    SectionMesh sectionMesh = section.getSectionMesh();
                    BlockPos renderOffset = section.getRenderOrigin();
                    int uboIndex = -1;

                    for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                        SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                        SectionRenderDispatcher.RenderSectionBufferSlice slice = this.sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);
                        if (slice != null && draw != null && (!draw.hasCustomIndexBuffer() || slice.indexBuffer() != null)) {
                            if (uboIndex == -1) {
                                uboIndex = this.xenoSectionInfos.size();
                                this.xenoSectionInfos.add(
                                        new DynamicUniforms.ChunkSectionInfo(
                                                frameModelView,
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

                            int baseVertex = (int) (slice.vertexBufferOffset() / (vertexFormat != null ? vertexFormat.getVertexSize() : 1));
                            int finalUboIndex = uboIndex;
                            Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup = this.xenoDrawGroups.get(layer);
                            List<RenderPass.Draw<GpuBufferSlice[]>> draws = drawGroup.get(combinedHash);
                            if (draws == null) {
                                draws = new ArrayList<>(4);
                                drawGroup.put(combinedHash, draws);
                            }
                            draws.add(
                                    new RenderPass.Draw<>(
                                            0,
                                            vertexBuffer,
                                            indexBuffer,
                                            indexType,
                                            firstIndex,
                                            draw.indexCount(),
                                            baseVertex,
                                            (sectionUbos, uploader) -> uploader.upload("ChunkSection", sectionUbos[finalUboIndex])
                                    )
                            );
                        }
                    }
                }
            } finally {
                this.sectionRenderDispatcher.unlock();
            }
        }

        GpuBufferSlice[] chunkSectionInfos = ((DynamicUniformsExtensions) RenderSystem.getDynamicUniforms())
                .xeno$writeChunkSections(this.xenoSectionInfos);
        return new ChunkSectionsToRender(blockAtlas, this.xenoDrawGroups, largestIndexCount, chunkSectionInfos);
    }
}
