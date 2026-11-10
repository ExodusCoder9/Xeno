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

import com.mojang.blaze3d.vertex.VertexSorting;
import com.xeno.client.common.render.chunk.XenoDistanceSorting;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class XenoRenderSectionSortMixin {
    /**
     * @author ExodusCoder9
     * @reason Supply DistanceSorting with relative coordinates for our BSP/Radix sorting.
     */
    @Overwrite
    private VertexSorting createVertexSorting(SectionPos sectionPos, Vec3 cameraPos) {
        return new XenoDistanceSorting(
            (float) (cameraPos.x - sectionPos.minBlockX()),
            (float) (cameraPos.y - sectionPos.minBlockY()),
            (float) (cameraPos.z - sectionPos.minBlockZ())
        );
    }
}
