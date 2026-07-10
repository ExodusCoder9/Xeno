package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SectionCompiler.class, priority = 500)
public abstract class SectionCompilerMixin {
    @Shadow @Final private boolean ambientOcclusion;
    @Shadow @Final private boolean cutoutLeaves;
    @Shadow @Final private BlockStateModelSet blockModelSet;
    @Shadow @Final private FluidStateModelSet fluidModelSet;
    @Shadow @Final private BlockColors blockColors;
    @Shadow protected abstract BufferBuilder getOrBeginLayer(Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack buffers, ChunkSectionLayer layer);
    @Shadow protected abstract <E extends BlockEntity> void handleBlockEntity(SectionCompiler.Results results, E blockEntity);

    @Unique
    private static final ThreadLocal<Map<ChunkSectionLayer, BufferBuilder>> XENO_STARTED_LAYERS = ThreadLocal.withInitial(() -> new EnumMap<>(ChunkSectionLayer.class));

    @Unique
    private static final ThreadLocal<ModelBlockRenderer> XENO_BLOCK_RENDERER = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<FluidRenderer> XENO_FLUID_RENDERER = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> XENO_MUTABLE_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void xenoFastCompile(SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        SectionCompiler.Results results = new SectionCompiler.Results();
        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();

        Map<ChunkSectionLayer, BufferBuilder> startedLayers = XENO_STARTED_LAYERS.get();
        startedLayers.clear();

        ModelBlockRenderer blockRenderer = XENO_BLOCK_RENDERER.get();
        if (blockRenderer == null) {
            blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
            XENO_BLOCK_RENDERER.set(blockRenderer);
        }

        FluidRenderer fluidRenderer = XENO_FLUID_RENDERER.get();
        if (fluidRenderer == null) {
            fluidRenderer = new FluidRenderer(this.fluidModelSet);
            XENO_FLUID_RENDERER.set(fluidRenderer);
        }

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };

        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };

        FluidRenderer.Output fluidOutput = layer -> this.getOrBeginLayer(startedLayers, builders, layer);

        BlockPos.MutableBlockPos pos = XENO_MUTABLE_POS.get();
        int minX = sectionPos.minBlockX();
        int minY = sectionPos.minBlockY();
        int minZ = sectionPos.minBlockZ();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    pos.set(minX + x, minY + y, minZ + z);
                    BlockState blockState = region.getBlockState(pos);

                    if (blockState.isAir()) continue;

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
                                    (float) x, (float) y, (float) z,
                                    region, pos, blockState,
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

        // Return early, bypassing standard Vanilla and Fabric API code.
        cir.setReturnValue(results);
    }
}