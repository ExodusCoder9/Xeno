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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.client.mixin.XenoItemFeatureRendererAccessor;
import com.xeno.fabric.client.render.frapi.quad.XenoQuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedItemSubmit;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class XenoExtendedItemRenderer extends RenderTypeFeatureRenderer<ExtendedItemSubmit> {
    private final XenoQuadEmitter emitter;
    private final XenoItemDecalGenerator decalGenerator = new XenoItemDecalGenerator();
    private final Matrix4f cameraInversePose = new Matrix4f();
    private final Matrix3f normalInversePose = new Matrix3f();

    private ExtendedItemSubmit currentSubmit;
    private boolean isDecalPrepared = false;
    private OutputMode outputMode = OutputMode.MAIN;

    public XenoExtendedItemRenderer() {
        this.emitter = new XenoQuadEmitter(this::handleQuad);
    }

    @Override
    protected void buildGroup(@NonNull FeatureFrameContext context, List<ExtendedItemSubmit> submits) {
        for (ExtendedItemSubmit submit : submits) {
            this.currentSubmit = submit;
            this.outputMode = submit.outlineColor() != 0 ? OutputMode.OUTLINE : OutputMode.MAIN;
            this.isDecalPrepared = false;

            this.emitter.clear();
            List<BakedQuad> vanillaQuads = submit.quads();
            for (int i = 0; i < vanillaQuads.size(); i++) {
                this.emitter.fromBakedQuad(vanillaQuads.get(i));
                this.emitter.emit();
            }

            if (submit.mesh() != null) {
                submit.mesh().outputTo(this.emitter);
            }
        }
        this.currentSubmit = null;
        this.isDecalPrepared = false;
        this.decalGenerator.clear();
    }

    private void handleQuad(MutableQuadView quad) {
        if (this.outputMode == OutputMode.MAIN) {
            bufferMain(quad);
        } else {
            bufferOutline(quad);
        }
    }

    private void bufferMain(MutableQuadView quad) {
        if (quad.emissive()) {
            quad.lightmap(LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT);
        } else {
            quad.minLightmap(this.currentSubmit.lightCoords());
        }

        int tintIndex = quad.tintIndex();
        int[] tintLayers = this.currentSubmit.tintLayers();
        if (tintIndex >= 0 && tintIndex < tintLayers.length) {
            quad.multiplyColor(tintLayers[tintIndex]);
        }

        ItemStackRenderState.FoilType quadFoil = quad.foilType();
        ItemStackRenderState.FoilType effectiveFoil = quadFoil != null ? quadFoil : this.currentSubmit.foilType();

        RenderType renderType = switch (effectiveFoil) {
            case NONE -> quad.itemRenderType();
            case STANDARD -> quad.itemGlintRenderType();
            case SPECIAL -> quad.itemGlintSpecialRenderType();
        };

        VertexConsumer vc = getVertexBuilder(renderType);

        if (effectiveFoil == ItemStackRenderState.FoilType.SPECIAL) {
            if (!this.isDecalPrepared) {
                PoseStack.Pose foilPose = XenoItemFeatureRendererAccessor.xeno$computeFoilDecalPose(
                    this.currentSubmit.displayContext(), this.currentSubmit.pose()
                );
                this.cameraInversePose.set(foilPose.pose()).invert();
                this.normalInversePose.set(foilPose.normal()).invert();
                this.isDecalPrepared = true;
            }
            this.decalGenerator.prepare(vc, this.cameraInversePose, this.normalInversePose, ItemFeatureRenderer.SPECIAL_FOIL_TEXTURE_SCALE);
            vc = this.decalGenerator;
        }

        quad.buffer(this.currentSubmit.overlayCoords(), this.currentSubmit.pose(), vc);
    }

    private void bufferOutline(MutableQuadView quad) {
        RenderType renderType = quad.itemRenderType().outline().orElse(null);
        if (renderType != null) {
            int outlineColor = this.currentSubmit.outlineColor();
            quad.color(outlineColor, outlineColor, outlineColor, outlineColor);
            quad.buffer(this.currentSubmit.overlayCoords(), this.currentSubmit.pose(), getVertexBuilder(renderType));
        }
    }

    private enum OutputMode {
        MAIN,
        OUTLINE
    }
}
