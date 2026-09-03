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

package com.xeno.client.common.render.chunk;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class XenoVertexFormats {
    public static final int TERRAIN_VERTEX_SIZE = 20;

    public static final VertexFormat TERRAIN = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RG32_UINT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG16_UNORM)
            .addAttribute("UV2", GpuFormat.RGBA8_UINT)
            .build();

    private XenoVertexFormats() {
    }
}
