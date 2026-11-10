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
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.Octree$Leaf")
public abstract class XenoOctreeLeafMixin {
    @Shadow
    @Final
    private SectionRenderDispatcher.RenderSection section;

    @Redirect(
            method = "visitNodes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"
            )
    )
    private boolean xeno$fastSectionCull(Frustum frustum, AABB bb) {
        if (frustum instanceof XenoFrustumProvider provider) {
            XenoBranchlessFrustum bf = provider.xeno$getBranchlessFrustum();
            BlockPos origin = this.section.getRenderOrigin();
            float relCenterX = (float) (origin.getX() + 8 - bf.getCamX());
            float relCenterY = (float) (origin.getY() + 8 - bf.getCamY());
            float relCenterZ = (float) (origin.getZ() + 8 - bf.getCamZ());
            return bf.isSectionVisibleRel(relCenterX, relCenterY, relCenterZ);
        }
        return frustum.isVisible(bb);
    }
}
