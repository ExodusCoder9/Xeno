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
import com.xeno.client.mixin.RenderSectionRegionAccessor;
import com.xeno.client.mixin.SectionCopyAccessor;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;

public final class XenoSectionCompiler extends SectionCompiler {
    private static final int SECTION_MAX = 15;

    private final boolean ambientOcclusion;
    private final boolean cutoutLeaves;
    private final BlockStateModelSet blockModelSet;
    private final FluidStateModelSet fluidModelSet;
    private final BlockColors blockColors;
    private final boolean fastPathCompatible;
    private final ThreadLocal<Workspace> workspaces = ThreadLocal.withInitial(Workspace::new);

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
        boolean compatible = true;
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            if (!isBlockLayout(layer.vertexFormat())) {
                compatible = false;
                break;
            }
        }

        this.fastPathCompatible = compatible;
    }

    @Override
    public @NonNull Results compile(@NonNull SectionPos sectionPos, @NonNull RenderSectionRegion region, @NonNull VertexSorting vertexSorting, @NonNull SectionBufferBuilderPack builders) {
        if (!this.fastPathCompatible) {
            return super.compile(sectionPos, region, vertexSorting, builders);
        }

        Results results = new Results();
        BlockPos minPos = sectionPos.origin();
        int minX = minPos.getX();
        int minY = minPos.getY();
        int minZ = minPos.getZ();
        VisGraph visGraph = new VisGraph();
        SectionCopy sectionCopy = ((RenderSectionRegionAccessor) region).xeno$getSection(sectionPos.x(), sectionPos.y(), sectionPos.z());
        if (((SectionCopyAccessor) sectionCopy).xeno$getSection() == null) {
            results.visibilitySet = visGraph.resolve();
            return results;
        }
        XenoLightDataCache lightDataCache = XenoLightDataCache.get().reset(sectionPos);
        Workspace workspace = this.workspaces.get();
        workspace.bind(builders, lightDataCache);
        XenoModelRenderer blockRenderer = workspace.blockRenderer;
        XenoFluidRenderer fluidRenderer = workspace.fluidRenderer;
        XenoSectionLayerBuffer[] sinks = workspace.sinks;

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
                                fluidRenderer.tesselate(region, currentPos, workspace.fluidOutput, blockState, fluidState);
                            }
                            if (blockState.getRenderShape() == RenderShape.MODEL) {
                                blockRenderer.emit(
                                    ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? workspace.opaqueQuadOutput : workspace.quadOutput,
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

    private XenoSectionLayerBuffer sink(XenoSectionLayerBuffer[] sinks, SectionBufferBuilderPack builders, ChunkSectionLayer layer) {
        int ordinal = layer.ordinal();
        XenoSectionLayerBuffer sink = sinks[ordinal];
        if (sink == null) {
            sink = new XenoSectionLayerBuffer(builders.buffer(layer), layer.vertexFormat(), layer);
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

    private final class Workspace {
        final XenoSectionLayerBuffer[] sinks = new XenoSectionLayerBuffer[ChunkSectionLayer.values().length];
        XenoModelRenderer blockRenderer;
        XenoFluidRenderer fluidRenderer;
        BlockQuadOutput quadOutput;
        BlockQuadOutput opaqueQuadOutput;
        XenoFluidRenderer.Output fluidOutput;
        SectionBufferBuilderPack boundPack;
        XenoLightDataCache boundCache;

        void bind(SectionBufferBuilderPack pack, XenoLightDataCache cache) {
            if (this.boundPack == pack && this.boundCache == cache) {
                for (XenoSectionLayerBuffer xenoSectionLayerBuffer : this.sinks) {
                    if (xenoSectionLayerBuffer != null) {
                        xenoSectionLayerBuffer.resetForReuse();
                    }
                }
                return;
            }

            java.util.Arrays.fill(this.sinks, null);
            this.blockRenderer = new XenoModelRenderer(XenoSectionCompiler.this.ambientOcclusion, true, XenoSectionCompiler.this.blockColors, cache);
            this.fluidRenderer = new XenoFluidRenderer(XenoSectionCompiler.this.fluidModelSet);
            this.quadOutput = (x, y, z, quad, instance) -> {
                XenoSectionLayerBuffer sink = XenoSectionCompiler.this.sink(this.sinks, pack, quad.materialInfo().layer());
                sink.writeBlockQuad(x, y, z, quad, instance);
            };
            this.opaqueQuadOutput = (x, y, z, quad, instance) -> {
                XenoSectionLayerBuffer sink = XenoSectionCompiler.this.sink(this.sinks, pack, ChunkSectionLayer.SOLID);
                sink.writeBlockQuad(x, y, z, quad, instance);
            };
            this.fluidOutput = layer -> XenoSectionCompiler.this.sink(this.sinks, pack, layer);
            this.boundPack = pack;
            this.boundCache = cache;
        }
    }
}
