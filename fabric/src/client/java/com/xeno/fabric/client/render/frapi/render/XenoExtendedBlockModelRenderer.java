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

package com.xeno.fabric.client.render.frapi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.fabric.client.render.frapi.quad.XenoQuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedBlockModelSubmit;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public final class XenoExtendedBlockModelRenderer extends RenderTypeFeatureRenderer<ExtendedBlockModelSubmit> {
    private static final Direction[] DIRECTIONS = Direction.values();
    private final QuadInstance quadInstance = new QuadInstance();
    private final XenoQuadEmitter emitter;
    private ExtendedBlockModelSubmit currentSubmit;
    private final LayerBufferCache bufferCache = new LayerBufferCache();

    public XenoExtendedBlockModelRenderer() {
        this.emitter = new XenoQuadEmitter(this::bufferMeshQuad);
    }

    @Override
    protected void buildGroup(@NonNull FeatureFrameContext context, List<ExtendedBlockModelSubmit> submits) {
        for (ExtendedBlockModelSubmit submit : submits) {
            this.currentSubmit = submit;
            this.bufferCache.prepare(submit.renderTypeFunction(), submit.sheetedDecalPose());

            this.quadInstance.setLightCoords(submit.lightCoords());
            this.quadInstance.setOverlayCoords(submit.overlayCoords());

            for (BlockStateModelPart part : submit.modelParts()) {
                putModelPartQuads(part, submit.pose(), submit.tintColor(), submit.tintLayers());
            }

            if (submit.mesh() != null) {
                this.emitter.clear();
                submit.mesh().outputTo(this.emitter);
            }
        }
        this.bufferCache.clear();
        this.currentSubmit = null;
    }

    private void putModelPartQuads(BlockStateModelPart part, PoseStack.Pose pose, int baseColor, int[] tintLayers) {
        for (Direction dir : DIRECTIONS) {
            for (BakedQuad quad : part.getQuads(dir)) {
                putSingleQuad(pose, quad, baseColor, tintLayers);
            }
        }
        for (BakedQuad quad : part.getQuads(null)) {
            putSingleQuad(pose, quad, baseColor, tintLayers);
        }
    }

    private void putSingleQuad(PoseStack.Pose pose, BakedQuad quad, int baseColor, int[] tintLayers) {
        VertexConsumer buffer = this.bufferCache.getBuffer(quad.materialInfo().layer());
        if (buffer == null) return;

        int tintIndex = quad.materialInfo().tintIndex();
        boolean useTint = tintIndex >= 0 && tintIndex < tintLayers.length;
        this.quadInstance.setColor(useTint ? ARGB.multiply(baseColor, tintLayers[tintIndex]) : baseColor);
        buffer.putBakedQuad(pose, quad, this.quadInstance);
    }

    private void bufferMeshQuad(MutableQuadView quad) {
        VertexConsumer buffer = this.bufferCache.getBuffer(quad.chunkLayer());
        if (buffer == null) return;

        if (quad.emissive()) {
            quad.lightmap(LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT);
        } else {
            quad.minLightmap(this.currentSubmit.lightCoords());
        }

        int tintIndex = quad.tintIndex();
        int[] tintLayers = this.currentSubmit.tintLayers();
        int baseColor = this.currentSubmit.tintColor();
        boolean useTint = tintIndex >= 0 && tintIndex < tintLayers.length;
        quad.multiplyColor(useTint ? ARGB.multiply(baseColor, tintLayers[tintIndex]) : baseColor);

        quad.buffer(this.currentSubmit.overlayCoords(), this.currentSubmit.pose(), buffer);
    }

    private final class LayerBufferCache {
        private Function<ChunkSectionLayer, @Nullable RenderType> renderTypeFn;
        private PoseStack.@Nullable Pose decalPose;
        private @Nullable ChunkSectionLayer cachedLayer;
        private @Nullable VertexConsumer cachedBuffer;

        void prepare(Function<ChunkSectionLayer, @Nullable RenderType> fn, PoseStack.@Nullable Pose decalPose) {
            this.renderTypeFn = fn;
            this.decalPose = decalPose;
            this.cachedLayer = null;
            this.cachedBuffer = null;
        }

        void clear() {
            this.renderTypeFn = null;
            this.decalPose = null;
            this.cachedLayer = null;
            this.cachedBuffer = null;
        }

        @Nullable VertexConsumer getBuffer(ChunkSectionLayer layer) {
            if (layer != this.cachedLayer) {
                this.cachedLayer = layer;
                RenderType renderType = this.renderTypeFn != null ? this.renderTypeFn.apply(layer) : null;
                if (renderType == null) {
                    this.cachedBuffer = null;
                } else {
                    VertexConsumer raw = XenoExtendedBlockModelRenderer.this.getVertexBuilder(renderType);
                    this.cachedBuffer = this.decalPose != null ? new SheetedDecalTextureGenerator(raw, this.decalPose, 1.0F) : raw;
                }
            }
            return this.cachedBuffer;
        }
    }
}
