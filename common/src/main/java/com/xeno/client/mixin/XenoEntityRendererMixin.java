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

import com.xeno.client.common.render.culling.XenoFrustumProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class XenoEntityRendererMixin<T extends Entity> {
    @Shadow
    protected abstract boolean affectedByCulling(T entity);

    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity, float partialTicks);

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void xeno$branchlessShouldRender(
            T entity,
            Frustum culler,
            double camX,
            double camY,
            double camZ,
            float partialTicks,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            cir.setReturnValue(false);
            return;
        }

        if (!this.affectedByCulling(entity)) {
            cir.setReturnValue(true);
            return;
        }

        final AABB box = this.getBoundingBoxForCulling(entity, partialTicks);
        if (box.hasNaN() || box.getSize() <= 0.0) {
            if (culler instanceof XenoFrustumProvider provider) {
                final double ex = entity.getX();
                final double ey = entity.getY();
                final double ez = entity.getZ();
                final boolean visible = provider.xeno$getBranchlessFrustum().isVisible(
                        ex - 2.5, ey - 2.5, ez - 2.5,
                        ex + 2.5, ey + 2.5, ez + 2.5
                );
                cir.setReturnValue(visible);
            }
            return;
        }

        if (culler instanceof XenoFrustumProvider provider) {
            final boolean visible = provider.xeno$getBranchlessFrustum().isVisible(
                    box.minX - 0.5, box.minY - 0.5, box.minZ - 0.5,
                    box.maxX + 0.5, box.maxY + 0.5, box.maxZ + 0.5
            );
            cir.setReturnValue(visible);
        }
    }
}
