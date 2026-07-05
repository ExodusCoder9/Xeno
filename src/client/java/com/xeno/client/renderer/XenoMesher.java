package com.xeno.client.renderer;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.builders.UVPair;
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
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;


public class XenoMesher extends SectionCompiler {
    private static final int VERTEX_SIZE = 28;
    private static final int QUAD_SIZE = 112;
    private static final int MAX_VERTICES = 16777215;
    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private final boolean ambientOcclusion;
    private final boolean cutoutLeaves;
    private final BlockStateModelSet blockModelSet;
    private final FluidStateModelSet fluidModelSet;
    private final BlockColors blockColors;

    public XenoMesher(
        boolean ambientOcclusion,
        boolean cutoutLeaves,
        BlockStateModelSet blockModelSet,
        FluidStateModelSet fluidModelSet,
        BlockColors blockColors
    ) {
        super(ambientOcclusion, cutoutLeaves, blockModelSet, fluidModelSet, blockColors);
        this.ambientOcclusion = ambientOcclusion;
        this.cutoutLeaves = cutoutLeaves;
        this.blockModelSet = blockModelSet;
        this.fluidModelSet = fluidModelSet;
        this.blockColors = blockColors;
    }

    @Override
    public SectionCompiler.Results compile(
        SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting, SectionBufferBuilderPack builders
    ) {
        SectionCompiler.Results results = new SectionCompiler.Results();
        BlockPos minPos = sectionPos.origin();
        BlockPos maxPos = minPos.offset(15, 15, 15);
        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();

        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
        FluidRenderer fluidRenderer = new FluidRenderer(this.fluidModelSet);
        Map<ChunkSectionLayer, ByteBufferBuilder> layerBuilders = new EnumMap<>(ChunkSectionLayer.class);
        DirectVertexConsumer fluidConsumer = new DirectVertexConsumer();

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            ByteBufferBuilder buf = getOrCreateBuffer(layerBuilders, builders, quad.materialInfo().layer());
            writeQuad(buf, x, y, z, quad, instance);
        };

        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            ByteBufferBuilder buf = getOrCreateBuffer(layerBuilders, builders, ChunkSectionLayer.SOLID);
            writeQuad(buf, x, y, z, quad, instance);
        };

        FluidRenderer.Output fluidOutput = layer -> {
            ByteBufferBuilder buf = getOrCreateBuffer(layerBuilders, builders, layer);
            fluidConsumer.setBuffer(buf);
            return fluidConsumer;
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
                            results.blockEntities.add(blockEntity);
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

        for (Map.Entry<ChunkSectionLayer, ByteBufferBuilder> entry : layerBuilders.entrySet()) {
            ChunkSectionLayer layer = entry.getKey();
            ByteBufferBuilder buf = entry.getValue();
            ByteBufferBuilder.Result vertexResult = buf.build();
            if (vertexResult == null) continue;

            int vertexCount = vertexResult.size() / VERTEX_SIZE;
            int indices = PrimitiveTopology.QUADS.indexCount(vertexCount);
            IndexType indexType = IndexType.least(vertexCount);
            MeshData mesh = new MeshData(vertexResult, new MeshData.DrawState(
                layer.vertexFormat(), vertexCount, indices, PrimitiveTopology.QUADS, indexType
            ));

            if (layer == ChunkSectionLayer.TRANSLUCENT) {
                results.transparencyState = mesh.sortQuads(builders.buffer(layer), vertexSorting);
            }

            results.renderedLayers.put(layer, mesh);
        }

        BlockModelLighter.clearCache();
        results.visibilitySet = visGraph.resolve();
        return results;
    }

    private static ByteBufferBuilder getOrCreateBuffer(
        Map<ChunkSectionLayer, ByteBufferBuilder> layerBuilders, SectionBufferBuilderPack builders, ChunkSectionLayer layer
    ) {
        ByteBufferBuilder buf = layerBuilders.get(layer);
        if (buf == null) {
            buf = builders.buffer(layer);
            layerBuilders.put(layer, buf);
        }
        return buf;
    }

    private static void writeQuad(
        ByteBufferBuilder buf, float x, float y, float z, BakedQuad quad, QuadInstance instance
    ) {
        long ptr = buf.reserve(QUAD_SIZE);
        var normal = quad.direction().getUnitVec3f();
        int lightEmission = quad.materialInfo().lightEmission();

        for (int v = 0; v < 4; v++) {
            var pos = quad.position(v);
            long packedUv = quad.packedUV(v);
            int vertexColor = instance.getColor(v);
            int light = instance.getLightCoordsWithEmission(v, lightEmission);
            float u = UVPair.unpackU(packedUv);
            float vt = UVPair.unpackV(packedUv);

            MemoryIntrinsics.putFloat(ptr, pos.x() + x);
            MemoryIntrinsics.putFloat(ptr + 4, pos.y() + y);
            MemoryIntrinsics.putFloat(ptr + 8, pos.z() + z);
            putRgba(ptr + 12, vertexColor);
            MemoryIntrinsics.putFloat(ptr + 16, u);
            MemoryIntrinsics.putFloat(ptr + 20, vt);
            putPackedUv(ptr + 24, light);
            ptr += VERTEX_SIZE;
        }
    }

    private static void putRgba(long pointer, int argb) {
        int abgr = ARGB.toABGR(argb);
        MemoryIntrinsics.putInt(pointer, IS_LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
    }

    private static void putPackedUv(long pointer, int packedUv) {
        if (IS_LITTLE_ENDIAN) {
            MemoryIntrinsics.putInt(pointer, packedUv);
        } else {
            MemoryIntrinsics.putShort(pointer, (short)(packedUv & 65535));
            MemoryIntrinsics.putShort(pointer + 2, (short)(packedUv >> 16 & 65535));
        }
    }

    private static class DirectVertexConsumer implements VertexConsumer {
        private ByteBufferBuilder buffer;
        private long vertexPointer = -1L;
        private int vertices;

        void setBuffer(ByteBufferBuilder buffer) {
            this.buffer = buffer;
            this.vertexPointer = -1L;
            this.vertices = 0;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (this.vertices >= MAX_VERTICES) {
                throw new IllegalStateException("Too many vertices");
            }
            this.vertices++;
            long ptr = this.buffer.reserve(VERTEX_SIZE);
            this.vertexPointer = ptr;
            MemoryIntrinsics.putFloat(ptr, x);
            MemoryIntrinsics.putFloat(ptr + 4, y);
            MemoryIntrinsics.putFloat(ptr + 8, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                MemoryIntrinsics.putByte(ptr + 12, (byte)r);
                MemoryIntrinsics.putByte(ptr + 13, (byte)g);
                MemoryIntrinsics.putByte(ptr + 14, (byte)b);
                MemoryIntrinsics.putByte(ptr + 15, (byte)a);
            }
            return this;
        }

        @Override
        public @NonNull VertexConsumer setColor(int color) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                putRgba(ptr + 12, color);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                MemoryIntrinsics.putFloat(ptr + 16, u);
                MemoryIntrinsics.putFloat(ptr + 20, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                MemoryIntrinsics.putShort(ptr + 24, (short)u);
                MemoryIntrinsics.putShort(ptr + 26, (short)v);
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public void addVertex(
            float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz
        ) {
            if (this.vertices >= MAX_VERTICES) {
                throw new IllegalStateException("Too many vertices");
            }
            this.vertices++;
            long ptr = this.buffer.reserve(VERTEX_SIZE);
            this.vertexPointer = ptr;
            MemoryIntrinsics.putFloat(ptr, x);
            MemoryIntrinsics.putFloat(ptr + 4, y);
            MemoryIntrinsics.putFloat(ptr + 8, z);
            putRgba(ptr + 12, color);
            MemoryIntrinsics.putFloat(ptr + 16, u);
            MemoryIntrinsics.putFloat(ptr + 20, v);
            putPackedUv(ptr + 24, lightCoords);
        }
    }
}
