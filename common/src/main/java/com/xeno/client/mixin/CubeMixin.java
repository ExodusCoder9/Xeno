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

package com.xeno.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xeno.client.common.render.entity.FastCuboidRenderer;
import com.xeno.client.common.render.entity.XenoCuboidData;
import com.xeno.client.common.render.entity.XenoCuboidHolder;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
public class CubeMixin implements XenoCuboidHolder {

    @Unique
    private XenoCuboidData xeno$cuboidData;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        this.xeno$cuboidData = new XenoCuboidData((ModelPart.Cube)(Object)this);
    }

    @Override
    public XenoCuboidData xeno$getCuboidData() {
        if (this.xeno$cuboidData == null) {
            this.xeno$cuboidData = new XenoCuboidData((ModelPart.Cube)(Object)this);
        }
        return this.xeno$cuboidData;
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void onCompile(PoseStack.Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color, CallbackInfo ci) {
        FastCuboidRenderer.renderCuboid(this.xeno$getCuboidData(), pose, builder, lightCoords, overlayCoords, color);
        ci.cancel();
    }
}
