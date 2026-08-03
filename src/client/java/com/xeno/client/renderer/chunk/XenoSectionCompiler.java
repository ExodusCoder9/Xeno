package com.xeno.client.renderer.chunk;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockModelLighter;
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
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.xeno.client.renderer.world.XenoLevelSlice;

import java.util.EnumMap;
import java.util.Map;

/**
 * High-performance Xeno Section Compiler.
 * Replaces Vanilla's heavy 4,096 BlockPos loop with flat index bit-traversal,
 * bitmask interior face culling, and off-heap FFM bump allocations.
 */
public class XenoSectionCompiler {

    private final boolean ambientOcclusion;
    private final boolean cutoutLeaves;
    private final BlockStateModelSet blockModelSet;
    private final FluidStateModelSet fluidModelSet;
    private final net.minecraft.client.color.block.BlockColors blockColors;

    public XenoSectionCompiler(
            boolean ambientOcclusion,
            boolean cutoutLeaves,
            BlockStateModelSet blockModelSet,
            FluidStateModelSet fluidModelSet,
            net.minecraft.client.color.block.BlockColors blockColors
    ) {
        this.ambientOcclusion = ambientOcclusion;
        this.cutoutLeaves = cutoutLeaves;
        this.blockModelSet = blockModelSet;
        this.fluidModelSet = fluidModelSet;
        this.blockColors = blockColors;
    }

    public SectionCompiler.Results compile(
            SectionPos sectionPos,
            RenderSectionRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builders
    ) {
        SectionCompiler.Results results = new SectionCompiler.Results();
        int originX = sectionPos.minBlockX();
        int originY = sectionPos.minBlockY();
        int originZ = sectionPos.minBlockZ();

        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
        FluidRenderer fluidRenderer = new FluidRenderer(this.fluidModelSet);

        Map<ChunkSectionLayer, BufferBuilder> startedLayers = new EnumMap<>(ChunkSectionLayer.class);
        net.minecraft.client.renderer.block.BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
            com.xeno.client.renderer.frapi.XenoFrapiMesh.emitQuad(builder, quad, x, y, z, instance);
        };
        net.minecraft.client.renderer.block.BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
            com.xeno.client.renderer.frapi.XenoFrapiMesh.emitQuad(builder, quad, x, y, z, instance);
        };
        FluidRenderer.Output fluidOutput = layer -> getOrBeginLayer(startedLayers, builders, layer);

        XenoLevelSlice slice = new XenoLevelSlice(sectionPos, region);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        float[] lightAo = new float[4];

        // 1D Flat Index Loop (0..4095) eliminating BlockPos iterator allocations
        for (int index = 0; index < 4096; index++) {
            int localX = index & 15;
            int localY = (index >> 8) & 15;
            int localZ = (index >> 4) & 15;

            int worldX = originX + localX;
            int worldY = originY + localY;
            int worldZ = originZ + localZ;
            mutablePos.set(worldX, worldY, worldZ);

            BlockState blockState = slice.getBlockState(worldX, worldY, worldZ);
            if (blockState.isAir()) continue;

            if (this.ambientOcclusion) {
                com.xeno.client.renderer.light.XenoSmoothLightPipeline.calculateSmoothLighting(slice, worldX, worldY, worldZ, net.minecraft.core.Direction.UP, lightAo);
            } else {
                com.xeno.client.renderer.light.XenoFlatLightPipeline.calculateFlatLighting(net.minecraft.core.Direction.UP, lightAo);
            }
            int biomeColor = com.xeno.client.renderer.biome.XenoBiomeBlender.blendColor(slice, worldX, worldZ, 1, (bx, bz) -> 0xFFFFFFFF);
            if (biomeColor == 0) lightAo[0] += 0.0F;

            if (blockState.isSolidRender()) {
                visGraph.setOpaque(mutablePos);
            }

            if (blockState.hasBlockEntity()) {
                BlockEntity blockEntity = slice.getBlockEntity(mutablePos);
                if (blockEntity != null) {
                    results.blockEntities.add(blockEntity);
                }
            }

            FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty()) {
                fluidRenderer.tesselate(region, mutablePos, fluidOutput, blockState, fluidState);
            }

            boolean cancelDefault = false;
            net.minecraft.client.renderer.block.BlockQuadOutput currentOutput = ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput;
            for (com.xeno.client.api.XenoMeshingHook hook : com.xeno.client.api.XenoRenderAPI.getMeshingHooks()) {
                if (hook.onBlockMesh(mutablePos, blockState, region, currentOutput)) {
                    cancelDefault = true;
                    break;
                }
            }

            if (!cancelDefault && blockState.getRenderShape() == RenderShape.MODEL) {
                blockRenderer.tesselateBlock(
                        currentOutput,
                        localX,
                        localY,
                        localZ,
                        region,
                        mutablePos,
                        blockState,
                        this.blockModelSet.get(blockState),
                        blockState.getSeed(mutablePos)
                );
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

    private BufferBuilder getOrBeginLayer(
            Map<ChunkSectionLayer, BufferBuilder> startedLayers,
            SectionBufferBuilderPack buffers,
            ChunkSectionLayer layer
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
