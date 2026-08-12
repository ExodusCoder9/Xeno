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

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.PrimitiveTopology;
import com.xeno.client.common.memory.MemoryAccess;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteOrder;

@Mixin(BufferBuilder.class)
public abstract class XenoBufferBuilderMixin implements VertexConsumer {
    @Final @Shadow
    private boolean blockFormat;
    @Final @Shadow
    private boolean entityFormat;
    @Shadow
    private long beginVertex() { return 0L; }

    @Unique
    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    @Unique
    private boolean xeno$particleFormat;
    @Unique
    private boolean xeno$positionColorTexLightmapFormat;

    @Inject(method = "<init>(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;Lcom/mojang/blaze3d/PrimitiveTopology;Lcom/mojang/blaze3d/vertex/VertexFormat;)V", at = @At("RETURN"))
    private void onInit(ByteBufferBuilder buffer, PrimitiveTopology primitiveTopology, VertexFormat format, CallbackInfo ci) {
        this.xeno$particleFormat = (format == DefaultVertexFormat.PARTICLE);
        this.xeno$positionColorTexLightmapFormat = (format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }

    /**
     * @author ExodusCoder9
     * @reason Use MemoryAcess to optimize BufferBuilder
     */
    @Overwrite
    public void addVertex(
        final float x, final float y, final float z, 
        final int color, 
        final float u, final float v, 
        final int overlayCoords, final int lightCoords, 
        final float nx, final float ny, final float nz
    ) {
        if (this.blockFormat || this.xeno$positionColorTexLightmapFormat) {
            long ptr = this.beginVertex();
            MemoryAccess.putFloat(ptr, x);
            MemoryAccess.putFloat(ptr + 4L, y);
            MemoryAccess.putFloat(ptr + 8L, z);
            xeno$putRgba(ptr + 12L, color);
            MemoryAccess.putFloat(ptr + 16L, u);
            MemoryAccess.putFloat(ptr + 20L, v);
            xeno$putPackedUv(ptr + 24L, lightCoords);
        } else if (this.entityFormat) {
            long ptr = this.beginVertex();
            MemoryAccess.putFloat(ptr, x);
            MemoryAccess.putFloat(ptr + 4L, y);
            MemoryAccess.putFloat(ptr + 8L, z);
            xeno$putRgba(ptr + 12L, color);
            MemoryAccess.putFloat(ptr + 16L, u);
            MemoryAccess.putFloat(ptr + 20L, v);
            xeno$putPackedUv(ptr + 24L, overlayCoords);
            xeno$putPackedUv(ptr + 28L, lightCoords);
            xeno$putNormals(ptr + 32L, nx, ny, nz);
        } else if (this.xeno$particleFormat) {
            long ptr = this.beginVertex();
            MemoryAccess.putFloat(ptr, x);
            MemoryAccess.putFloat(ptr + 4L, y);
            MemoryAccess.putFloat(ptr + 8L, z);
            MemoryAccess.putFloat(ptr + 12L, u);
            MemoryAccess.putFloat(ptr + 16L, v);
            xeno$putRgba(ptr + 20L, color);
            xeno$putPackedUv(ptr + 24L, lightCoords);
        } else {
            // Emulate VertexConsumer.super.addVertex manually to avoid Mixin resolution issues
            this.addVertex(x, y, z).setColor(color).setUv(u, v).setOverlay(overlayCoords).setLight(lightCoords).setNormal(nx, ny, nz);
        }
    }

    @Unique
    private static void xeno$putRgba(long pointer, int argb) {
        int abgr = ARGB.toABGR(argb);
        MemoryAccess.putInt(pointer, IS_LITTLE_ENDIAN ? abgr : Integer.reverseBytes(abgr));
    }

    @Unique
    private static void xeno$putPackedUv(long pointer, int packedUv) {
        if (IS_LITTLE_ENDIAN) {
            MemoryAccess.putInt(pointer, packedUv);
        } else {
            MemoryAccess.putShort(pointer, (short)(packedUv & 65535));
            MemoryAccess.putShort(pointer + 2L, (short)(packedUv >> 16 & 65535));
        }
    }

    @Unique
    private static void xeno$putNormals(long pointer, float nx, float ny, float nz) {
        MemoryAccess.putByte(pointer, xeno$normalIntValue(nx));
        MemoryAccess.putByte(pointer + 1L, xeno$normalIntValue(ny));
        MemoryAccess.putByte(pointer + 2L, xeno$normalIntValue(nz));
    }

    @Unique
    private static byte xeno$normalIntValue(float c) {
        return (byte)((int)(Mth.clamp(c, -1.0F, 1.0F) * 127.0F) & 0xFF);
    }
}
