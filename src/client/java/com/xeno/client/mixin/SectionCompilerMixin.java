package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.xeno.client.renderer.XenoMeshingCache;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

@Mixin(SectionCompiler.class)
@SuppressWarnings({"unused"})
public abstract class SectionCompilerMixin {
    @Shadow @Final
    private boolean ambientOcclusion;

    @Shadow @Final
    private boolean cutoutLeaves;

    @Shadow @Final
    private BlockStateModelSet blockModelSet;

    @Shadow @Final
    private FluidStateModelSet fluidModelSet;

    @Shadow @Final
    private BlockColors blockColors;

    @Shadow
    protected abstract void handleBlockEntity(SectionCompiler.Results results, BlockEntity blockEntity);

    @Shadow
    protected abstract BufferBuilder getOrBeginLayer(
        Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack buffers, ChunkSectionLayer layer
    );

    /**
     * @author Antigravity
     * @reason Overwrite compile to initialize and wrap execution with XenoMeshingCache.
     *         It also redirects region.getBlockState queries to use the fast flat array cache.
     */
    @Overwrite
    public SectionCompiler.Results compile(
        final SectionPos sectionPos, final RenderSectionRegion region, final VertexSorting vertexSorting, final SectionBufferBuilderPack builders
    ) {
        XenoMeshingCache cache = XenoMeshingCache.get();
        cache.init(region, sectionPos.origin());

        try {
            SectionCompiler.Results results = new SectionCompiler.Results();
            BlockPos minPos = sectionPos.origin();
            BlockPos maxPos = minPos.offset(15, 15, 15);
            VisGraph visGraph = new VisGraph();
            BlockModelLighter.enableCaching();
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
            FluidRenderer fluidRenderer = new FluidRenderer(this.fluidModelSet);
            Map<ChunkSectionLayer, BufferBuilder> startedLayers = new EnumMap<>(ChunkSectionLayer.class);

            BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
                BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
                builder.putBlockBakedQuad(x, y, z, quad, instance);
            };

            BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
                BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
                builder.putBlockBakedQuad(x, y, z, quad, instance);
            };

            FluidRenderer.Output fluidOutput = layerx -> this.getOrBeginLayer(startedLayers, builders, layerx);

            for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
                // Use fast flat cache lookup for the compiler block traversal loop
                BlockState blockState = cache.getBlockState(region, pos);
                if (!blockState.isAir()) {
                    try {
                        if (blockState.isSolidRender()) {
                            visGraph.setOpaque(pos);
                        }

                        if (blockState.hasBlockEntity()) {
                            BlockEntity blockEntity = region.getBlockEntity(pos);
                            if (blockEntity != null) {
                                this.handleBlockEntity(results, blockEntity);
                            }
                        }

                        FluidState fluidState = blockState.getFluidState();
                        if (!fluidState.isEmpty()) {
                            fluidRenderer.tesselate(region, pos, fluidOutput, blockState, fluidState);
                        }

                        if (blockState.getRenderShape() == RenderShape.MODEL) {
                            blockRenderer.tesselateBlock(
                                ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput,
                                SectionPos.sectionRelative(pos.getX()),
                                SectionPos.sectionRelative(pos.getY()),
                                SectionPos.sectionRelative(pos.getZ()),
                                region,
                                pos,
                                blockState,
                                this.blockModelSet.get(blockState),
                                blockState.getSeed(pos)
                            );
                        }
                    } catch (Throwable t) {
                        CrashReport report = CrashReport.forThrowable(t, "Tesselating block in world");
                        CrashReportCategory category = report.addCategory("Block being tesselated");
                        CrashReportCategory.populateBlockDetails(category, region, pos, blockState);
                        throw new ReportedException(report);
                    }
                }
            }

            for (Entry<ChunkSectionLayer, BufferBuilder> entry : startedLayers.entrySet()) {
                ChunkSectionLayer layer = entry.getKey();
                MeshData mesh = entry.getValue().build();
                if (mesh != null) {
                    if (layer == ChunkSectionLayer.TRANSLUCENT) {
                        results.transparencyState = mesh.sortQuads(builders.buffer(layer), vertexSorting);
                    }
                    results.renderedLayers.put(layer, mesh);
                }
            }

            BlockModelLighter.clearCache();
            results.visibilitySet = visGraph.resolve();
            return results;
        } finally {
            cache.disable();
        }
    }
}
