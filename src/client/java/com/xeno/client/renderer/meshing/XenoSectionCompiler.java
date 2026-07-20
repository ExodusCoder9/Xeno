package com.xeno.client.renderer.meshing;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.xeno.client.api.XenoMeshingHook;
import com.xeno.client.api.XenoRenderAPI;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
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

/**
 * Custom Section Compiler that contains the optimized chunk meshing logic.
 */
public final class XenoSectionCompiler {

    private static final ThreadLocal<Map<ChunkSectionLayer, BufferBuilder>> STARTED_LAYERS = ThreadLocal.withInitial(() -> new EnumMap<>(ChunkSectionLayer.class));
    private static final ThreadLocal<ModelBlockRenderer> BLOCK_RENDERER = new ThreadLocal<>();
    private static final ThreadLocal<FluidRenderer> FLUID_RENDERER = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    public static SectionCompiler.Results compile(
            SectionPos sectionPos,
            RenderSectionRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builders,
            BlockStateModelSet blockModelSet,
            FluidStateModelSet fluidModelSet,
            BlockColors blockColors,
            boolean ambientOcclusion,
            boolean cutoutLeaves
    ) {
        SectionCompiler.Results results = new SectionCompiler.Results();
        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();

        Map<ChunkSectionLayer, BufferBuilder> startedLayers = STARTED_LAYERS.get();
        startedLayers.clear();

        FluidRenderer fluidRenderer = FLUID_RENDERER.get();
        ModelBlockRenderer blockRenderer = BLOCK_RENDERER.get();

        if (fluidRenderer == null || fluidRenderer.fluidModels != fluidModelSet) {
            fluidRenderer = new FluidRenderer(fluidModelSet);
            FLUID_RENDERER.set(fluidRenderer);

            blockRenderer = new ModelBlockRenderer(ambientOcclusion, true, blockColors);
            BLOCK_RENDERER.set(blockRenderer);
        }

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };

        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };

        FluidRenderer.Output fluidOutput = layer -> getOrBeginLayer(startedLayers, builders, layer);

        BlockPos.MutableBlockPos pos = MUTABLE_POS.get();
        int minX = sectionPos.minBlockX();
        int minY = sectionPos.minBlockY();
        int minZ = sectionPos.minBlockZ();

        net.minecraft.client.renderer.chunk.SectionCopy[] sections = ((com.xeno.client.mixin.RenderSectionRegionAccessor) region).xeno$getSections();
        net.minecraft.client.renderer.chunk.SectionCopy centerSection = sections[13];

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    pos.set(minX + x, minY + y, minZ + z);
                    BlockState blockState = centerSection.getBlockState(pos);

                    if (blockState.isAir()) continue;

                    try {
                        if (blockState.isSolidRender()) {
                            visGraph.setOpaque(pos);
                        }

                        if (blockState.hasBlockEntity()) {
                            BlockEntity blockEntity = centerSection.getBlockEntity(pos);
                            if (blockEntity != null) {
                                results.blockEntities.add(blockEntity);
                            }
                        }

                        FluidState fluidState = blockState.getFluidState();
                        if (!fluidState.isEmpty()) {
                            fluidRenderer.tesselate(region, pos, fluidOutput, blockState, fluidState);
                        }

                        boolean cancelDefault = false;
                        BlockQuadOutput currentOutput = ModelBlockRenderer.forceOpaque(cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput;
                        for (XenoMeshingHook hook : XenoRenderAPI.getMeshingHooks()) {
                            if (hook.onBlockMesh(pos, blockState, region, currentOutput)) {
                                cancelDefault = true;
                                break;
                            }
                        }

                        if (!cancelDefault && blockState.getRenderShape() == RenderShape.MODEL) {
                            blockRenderer.tesselateBlock(
                                    currentOutput,
                                    (float) x, (float) y, (float) z,
                                    region, pos, blockState,
                                    blockModelSet.get(blockState),
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
        }

        for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : startedLayers.entrySet()) {
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
    }

    private static BufferBuilder getOrBeginLayer(
            Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack buffers, ChunkSectionLayer layer
    ) {
        BufferBuilder builder = startedLayers.get(layer);
        if (builder == null) {
            ByteBufferBuilder buffer = buffers.buffer(layer);
            builder = new BufferBuilder(buffer, PrimitiveTopology.QUADS, layer.vertexFormat());
            startedLayers.put(layer, builder);
        }
        return builder;
    }
}
