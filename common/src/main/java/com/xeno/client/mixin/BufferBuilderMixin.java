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

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.xeno.client.common.render.entity.XenoBufferWriter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements XenoBufferWriter {
    @Shadow private int vertices;
    @Final
    @Shadow private ByteBufferBuilder buffer;
    @Final
    @Shadow private int vertexSize;
    @Final
    @Shadow private boolean entityFormat;

    @Shadow private void ensureBuilding() {}
    @Shadow private void endLastVertex() {}

    @Override
    public boolean xeno$isEntityFormat() {
        return this.entityFormat;
    }

    @Override
    public long xeno$reserveVertices(int count) {
        this.ensureBuilding();
        this.endLastVertex();
        this.vertices += count;
        return this.buffer.reserve(this.vertexSize * count);
    }
}
