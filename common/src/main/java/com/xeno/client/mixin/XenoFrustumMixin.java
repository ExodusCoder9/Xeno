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
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.mixin;

import com.xeno.client.common.render.culling.XenoBranchlessFrustum;
import com.xeno.client.common.render.culling.XenoFrustumProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class XenoFrustumMixin implements XenoFrustumProvider {
    @Shadow
    private double camX;

    @Shadow
    private double camY;

    @Shadow
    private double camZ;

    @Shadow
    @Final
    private Matrix4f matrix;

    @Unique
    private final XenoBranchlessFrustum xeno$branchlessFrustum = new XenoBranchlessFrustum();

    @Inject(method = "calculateFrustum", at = @At("TAIL"))
    private void xeno$onCalculateFrustum(Matrix4fc modelView, Matrix4f projection, CallbackInfo ci) {
        this.xeno$branchlessFrustum.set(this.matrix, this.camX, this.camY, this.camZ);
    }

    @Inject(method = "set(Lnet/minecraft/client/renderer/culling/Frustum;)V", at = @At("TAIL"))
    private void xeno$onSet(Frustum frustum, CallbackInfo ci) {
        this.xeno$branchlessFrustum.set(this.matrix, this.camX, this.camY, this.camZ);
    }

    @Inject(method = "prepare", at = @At("TAIL"))
    private void xeno$onPrepare(double camX, double camY, double camZ, CallbackInfo ci) {
        this.xeno$branchlessFrustum.setCameraPos(camX, camY, camZ);
    }

    @Inject(method = "offset(F)Lnet/minecraft/client/renderer/culling/Frustum;", at = @At("TAIL"))
    private void xeno$onOffset(float offset, CallbackInfoReturnable<Frustum> cir) {
        this.xeno$branchlessFrustum.setCameraPos(this.camX, this.camY, this.camZ);
    }

    @Inject(method = "offsetToFullyIncludeCameraCube", at = @At("TAIL"))
    private void xeno$onOffsetCube(int cubeSize, CallbackInfoReturnable<Frustum> cir) {
        this.xeno$branchlessFrustum.setCameraPos(this.camX, this.camY, this.camZ);
    }

    /**
     * @author ExodusCoder9
     * @reason Replace JOML frustum AABB test with our frustum test.
     */
    @Overwrite
    public boolean isVisible(AABB aabb) {
        return this.xeno$branchlessFrustum.isVisible(aabb);
    }

    /**
     * @author ExodusCoder9
     * @reason Replace JOML cubeInFrustum with our branchless positive/negative vertex evaluation
     */
    @Overwrite
    private int cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.xeno$branchlessFrustum.cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public XenoBranchlessFrustum xeno$getBranchlessFrustum() {
        return this.xeno$branchlessFrustum;
    }
}
