package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import com.xeno.client.meshing.SectionFaceData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
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

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
    @Shadow @Final private boolean ambientOcclusion;
    @Shadow @Final private boolean cutoutLeaves;
    @Shadow @Final private BlockStateModelSet blockModelSet;
    @Shadow @Final private FluidStateModelSet fluidModelSet;
    @Shadow @Final private BlockColors blockColors;

    @Shadow
    private BufferBuilder getOrBeginLayer(
            Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack buffers, ChunkSectionLayer layer
    ) {
        throw new AssertionError();
    }

    @Shadow
    private <E extends BlockEntity> void handleBlockEntity(SectionCompiler.Results results, E blockEntity) {
        throw new AssertionError();
    }

    /**
     * @author ExodusCoder9
     * @reason Overwrites standard compilation to count face directions and record them for custom occlusion culling.
     */
    @Overwrite
    public SectionCompiler.Results compile(
            final SectionPos sectionPos,
            final RenderSectionRegion region,
            final VertexSorting vertexSorting,
            final SectionBufferBuilderPack builders
    ) {
        XenoClient.xenoSetCurrentSectionNode(sectionPos.asLong());

        try {
            SectionCompiler.Results results = new SectionCompiler.Results();
            BlockPos minPos = sectionPos.origin();
            BlockPos maxPos = minPos.offset(15, 15, 15);
            VisGraph visGraph = new VisGraph();
            BlockModelLighter.enableCaching();
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
            FluidRenderer fluidRenderer = new FluidRenderer(this.fluidModelSet);
            Map<ChunkSectionLayer, BufferBuilder> startedLayers = new EnumMap<>(ChunkSectionLayer.class);

            int[] perDirFaceCounts = new int[6];
            int[] totalVertices = new int[1]; // Using array to update within lambda

            BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
                if (quad.direction() != null) {
                    perDirFaceCounts[quad.direction().ordinal()]++;
                }
                totalVertices[0] += 4;
                BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
                builder.putBlockBakedQuad(x, y, z, quad, instance);
            };

            BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
                if (quad.direction() != null) {
                    perDirFaceCounts[quad.direction().ordinal()]++;
                }
                totalVertices[0] += 4;
                BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
                builder.putBlockBakedQuad(x, y, z, quad, instance);
            };

            FluidRenderer.Output fluidOutput = layerx -> {
                BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, layerx);
                return new VertexConsumer() {
                    @Override
                    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
                        totalVertices[0]++;
                        builder.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
                    }

                    @Override
                    public VertexConsumer setLineWidth(float width) {
                        builder.setLineWidth(width);
                        return this;
                    }

                    @Override
                    public VertexConsumer setNormal(float x, float y, float z) {
                        builder.setNormal(x, y, z);
                        return this;
                    }

                    @Override
                    public VertexConsumer setUv2(int u, int v) {
                        builder.setUv2(u, v);
                        return this;
                    }

                    @Override
                    public VertexConsumer setUv1(int u, int v) {
                        builder.setUv1(u, v);
                        return this;
                    }

                    @Override
                    public VertexConsumer setUv(float u, float v) {
                        builder.setUv(u, v);
                        return this;
                    }

                    @Override
                    public VertexConsumer setColor(int color) {
                        builder.setColor(color);
                        return this;
                    }

                    @Override
                    public VertexConsumer setColor(int r, int g, int b, int a) {
                        builder.setColor(r, g, b, a);
                        return this;
                    }

                    @Override
                    public VertexConsumer addVertex(float x, float y, float z) {
                        builder.addVertex(x, y, z);
                        return this;
                    }
                };
            };

            for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
                BlockState blockState = region.getBlockState(pos);
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

            // Record face data in our custom tracker!
            ViewArea viewArea = XenoClient.getViewArea();
            if (viewArea != null) {
                SectionRenderDispatcher.RenderSection renderSection = ((ViewAreaAccessor) viewArea).invokeGetRenderSection(sectionPos.asLong());
                if (renderSection != null) {
                    XenoClient.getSectionFaceData().record(renderSection.index, perDirFaceCounts, totalVertices[0]);
                }
            }

            return results;
        } finally {
            XenoClient.xenoClearCurrentSectionNode();
        }
    }
}
