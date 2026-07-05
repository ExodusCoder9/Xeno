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
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class XenoMesher extends SectionCompiler {
    private static final int VERTEX_SIZE = 16;
    private static final int QUAD_SIZE = 64;
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
        XenoMeshingCache.get().init(region, sectionPos.origin());

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

        XenoMeshingCache.get().disable();
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
        int lightEmission = quad.materialInfo().lightEmission();
        Vector3fc normal = quad.direction().getUnitVec3f();
        byte normalId = (byte) getNormalId(normal.x(), normal.y(), normal.z());

        for (int v = 0; v < 4; v++) {
            var pos = quad.position(v);
            long packedUv = quad.packedUV(v);
            int vertexColor = instance.getColor(v);
            int light = instance.getLightCoordsWithEmission(v, lightEmission);

            short posX = (short) Math.round((pos.x() + x) * 1000.0f);
            short posY = (short) Math.round((pos.y() + y) * 1000.0f);
            short posZ = (short) Math.round((pos.z() + z) * 1000.0f);

            MemoryIntrinsics.putShort(ptr, posX);
            MemoryIntrinsics.putShort(ptr + 2L, posY);
            MemoryIntrinsics.putShort(ptr + 4L, posZ);

            putRgba(ptr + 6L, vertexColor);

            short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
            short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);

            MemoryIntrinsics.putShort(ptr + 10L, texU);
            MemoryIntrinsics.putShort(ptr + 12L, texV);

            byte lightBlock = (byte) ((light & 0xFFFF) / 16);
            byte lightSky = (byte) (((light >> 16) & 0xFFFF) / 16);

            MemoryIntrinsics.putByte(ptr + 14L, (byte) (lightBlock | (normalId << 4)));
            MemoryIntrinsics.putByte(ptr + 15L, lightSky);

            ptr += VERTEX_SIZE;
        }
    }

    private static int getNormalId(float nx, float ny, float nz) {
        float absX = Math.abs(nx);
        float absY = Math.abs(ny);
        float absZ = Math.abs(nz);

        if (absX > absY && absX > absZ) {
            return nx > 0.0f ? 5 : 4;
        } else if (absY > absX && absY > absZ) {
            return ny > 0.0f ? 1 : 0;
        } else {
            return nz > 0.0f ? 3 : 2;
        }
    }

    private static void putRgba(long pointer, int argb) {
        int abgr = ARGB.toABGR(argb);
        MemoryIntrinsics.putInt(pointer, IS_LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
    }

    private static class DirectVertexConsumer implements VertexConsumer {
        private @Nullable ByteBufferBuilder buffer;
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
            assert this.buffer != null;
            long ptr = this.buffer.reserve(VERTEX_SIZE);
            this.vertexPointer = ptr;

            short posX = (short) Math.round(x * 1000.0f);
            short posY = (short) Math.round(y * 1000.0f);
            short posZ = (short) Math.round(z * 1000.0f);
            MemoryIntrinsics.putShort(ptr, posX);
            MemoryIntrinsics.putShort(ptr + 2L, posY);
            MemoryIntrinsics.putShort(ptr + 4L, posZ);

            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                MemoryIntrinsics.putByte(ptr + 6L, (byte)r);
                MemoryIntrinsics.putByte(ptr + 7L, (byte)g);
                MemoryIntrinsics.putByte(ptr + 8L, (byte)b);
                MemoryIntrinsics.putByte(ptr + 9L, (byte)a);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                putRgba(ptr + 6L, color);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                short texU = (short) Math.round(u * 32767.0f);
                short texV = (short) Math.round(v * 32767.0f);
                MemoryIntrinsics.putShort(ptr + 10L, texU);
                MemoryIntrinsics.putShort(ptr + 12L, texV);
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
                byte lightBlock = (byte) ((u & 0xFFFF) / 16);
                byte lightSky = (byte) ((v & 0xFFFF) / 16);
                byte currentId = (byte) (MemoryIntrinsics.getByte(ptr + 14L) & 0xF0);

                MemoryIntrinsics.putByte(ptr + 14L, (byte) (lightBlock | currentId));
                MemoryIntrinsics.putByte(ptr + 15L, lightSky);
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            long ptr = this.vertexPointer;
            if (ptr != -1L) {
                byte normalId = (byte) getNormalId(x, y, z);
                byte currentLight = (byte) (MemoryIntrinsics.getByte(ptr + 14L) & 0x0F);
                MemoryIntrinsics.putByte(ptr + 14L, (byte) (currentLight | (normalId << 4)));
            }
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
            assert this.buffer != null;
            long ptr = this.buffer.reserve(VERTEX_SIZE);
            this.vertexPointer = ptr;

            short posX = (short) Math.round(x * 1000.0f);
            short posY = (short) Math.round(y * 1000.0f);
            short posZ = (short) Math.round(z * 1000.0f);

            MemoryIntrinsics.putShort(ptr, posX);
            MemoryIntrinsics.putShort(ptr + 2L, posY);
            MemoryIntrinsics.putShort(ptr + 4L, posZ);

            putRgba(ptr + 6L, color);

            short texU = (short) Math.round(u * 32767.0f);
            short texV = (short) Math.round(v * 32767.0f);

            MemoryIntrinsics.putShort(ptr + 10L, texU);
            MemoryIntrinsics.putShort(ptr + 12L, texV);

            byte lightBlock = (byte) ((lightCoords & 0xFFFF) / 16);
            byte lightSky = (byte) (((lightCoords >> 16) & 0xFFFF) / 16);
            byte normalId = (byte) getNormalId(nx, ny, nz);

            MemoryIntrinsics.putByte(ptr + 14L, (byte) (lightBlock | (normalId << 4)));
            MemoryIntrinsics.putByte(ptr + 15L, lightSky);
        }
    }
}