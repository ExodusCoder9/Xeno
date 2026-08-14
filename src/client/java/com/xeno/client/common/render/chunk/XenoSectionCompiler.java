/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xeno.client.common.render.chunk;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import com.xeno.client.common.render.block.XenoLightDataCache;
import com.xeno.client.common.render.block.XenoModelRenderer;
import com.xeno.client.common.render.block.XenoFluidRenderer;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
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
import org.jspecify.annotations.NonNull;

public class XenoSectionCompiler extends SectionCompiler {
    private static final int SECTION_MIN = 0;
    private static final int SECTION_MAX = 15;

    private final boolean ambientOcclusion;
    private final boolean cutoutLeaves;
    private final BlockStateModelSet blockModelSet;
    private final FluidStateModelSet fluidModelSet;
    private final BlockColors blockColors;

    public XenoSectionCompiler(
        boolean ambientOcclusion,
        boolean cutoutLeaves,
        BlockStateModelSet blockModelSet,
        FluidStateModelSet fluidStateModelSet,
        BlockColors blockColors
    ) {
        super(ambientOcclusion, cutoutLeaves, blockModelSet, fluidStateModelSet, blockColors);
        this.ambientOcclusion = ambientOcclusion;
        this.cutoutLeaves = cutoutLeaves;
        this.blockModelSet = blockModelSet;
        this.fluidModelSet = fluidStateModelSet;
        this.blockColors = blockColors;
    }

    @Override
    public @NonNull Results compile(@NonNull SectionPos sectionPos, @NonNull RenderSectionRegion region, @NonNull VertexSorting vertexSorting, @NonNull SectionBufferBuilderPack builders) {
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            if (!isBlockLayout(layer.vertexFormat())) {
                return super.compile(sectionPos, region, vertexSorting, builders);
            }
        }

        Results results = new Results();
        int chunkRegionId = ((sectionPos.x() & 7) << 5) | ((sectionPos.y() & 3) << 0) | ((sectionPos.z() & 7) << 2);
        BlockPos minPos = sectionPos.origin();
        int minX = minPos.getX();
        int minY = minPos.getY();
        int minZ = minPos.getZ();
        VisGraph visGraph = new VisGraph();
        XenoLightDataCache lightDataCache = XenoLightDataCache.get().reset(sectionPos);
        XenoModelRenderer blockRenderer = new XenoModelRenderer(this.ambientOcclusion, true, this.blockColors, lightDataCache);
        XenoFluidRenderer fluidRenderer = new XenoFluidRenderer(this.fluidModelSet);
        XenoSectionLayerBuffer[] sinks = new XenoSectionLayerBuffer[ChunkSectionLayer.values().length];
        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            XenoSectionLayerBuffer sink = this.sink(sinks, builders, quad.materialInfo().layer(), chunkRegionId);
            sink.writeBlockQuad(x, y, z, quad, instance);
        };
        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            XenoSectionLayerBuffer sink = this.sink(sinks, builders, ChunkSectionLayer.SOLID, chunkRegionId);
            sink.writeBlockQuad(x, y, z, quad, instance);
        };
        XenoFluidRenderer.Output fluidOutput = layer -> this.sink(sinks, builders, layer, chunkRegionId);

        BlockPos.MutableBlockPos currentPos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= minY + SECTION_MAX; y++) {
            for (int z = minZ; z <= minZ + SECTION_MAX; z++) {
                for (int x = minX; x <= minX + SECTION_MAX; x++) {
                    currentPos.set(x, y, z);
                    BlockState blockState = region.getBlockState(currentPos);
                    if (!blockState.isAir()) {
                        try {
                            if (blockState.isSolidRender()) {
                                visGraph.setOpaque(currentPos);
                            }
                            if (blockState.hasBlockEntity()) {
                                BlockEntity blockEntity = region.getBlockEntity(currentPos);
                                if (blockEntity != null) {
                                    results.blockEntities.add(blockEntity);
                                }
                            }
                            FluidState fluidState = blockState.getFluidState();
                            if (!fluidState.isEmpty()) {
                                fluidRenderer.tesselate(region, currentPos, fluidOutput, blockState, fluidState);
                            }
                            if (blockState.getRenderShape() == RenderShape.MODEL) {
                                blockRenderer.emit(
                                    ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput,
                                    SectionPos.sectionRelative(x),
                                    SectionPos.sectionRelative(y),
                                    SectionPos.sectionRelative(z),
                                    region,
                                    currentPos,
                                    blockState,
                                    this.blockModelSet.get(blockState),
                                    blockState.getSeed(currentPos)
                                );
                            }
                        } catch (Throwable t) {
                            CrashReport report = CrashReport.forThrowable(t, "Tesselating block in world");
                            CrashReportCategory category = report.addCategory("Block being tesselated");
                            CrashReportCategory.populateBlockDetails(category, region, currentPos, blockState);
                            throw new ReportedException(report);
                        }
                    }
                }
            }
        }

        for (XenoSectionLayerBuffer sink : sinks) {
            if (sink == null) {
                continue;
            }
            MeshData mesh = sink.build();
            if (mesh != null) {
                if (sink.layer() == ChunkSectionLayer.TRANSLUCENT) {
                    results.transparencyState = mesh.sortQuads(builders.buffer(sink.layer()), vertexSorting);
                }

                results.renderedLayers.put(sink.layer(), mesh);
            }
        }

        results.visibilitySet = visGraph.resolve();
        return results;
    }

    private XenoSectionLayerBuffer sink(XenoSectionLayerBuffer[] sinks, SectionBufferBuilderPack builders, ChunkSectionLayer layer, int chunkRegionId) {
        int ordinal = layer.ordinal();
        XenoSectionLayerBuffer sink = sinks[ordinal];
        if (sink == null) {
            sink = new XenoSectionLayerBuffer(builders.buffer(layer), layer.vertexFormat(), layer, chunkRegionId);
            sinks[ordinal] = sink;
        }
        return sink;
    }

    private static boolean isBlockLayout(VertexFormat format) {
        if (format.getVertexSize() != 28) {
            return false;
        }
        VertexFormatElement position = format.getElement("Position");
        VertexFormatElement color = format.getElement("Color");
        VertexFormatElement uv0 = format.getElement("UV0");
        VertexFormatElement uv2 = format.getElement("UV2");
        return position != null
            && position.offset() == 0
            && color != null
            && color.offset() == 12
            && uv0 != null
            && uv0.offset() == 16
            && uv2 != null
            && uv2.offset() == 24;
    }
}
