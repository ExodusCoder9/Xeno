package com.xeno.client.mixin;

import com.xeno.client.culling.XenoVertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin {
    @Shadow @Final
    private VertexFormat format;

    @Shadow
    protected abstract long beginVertex();

    @Shadow
    private static void putRgba(final long pointer, final int argb) {
        throw new AssertionError();
    }

    @Inject(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), cancellable = true)
    private void xeno_addVertex(
            float x, float y, float z, int color, float u, float v,
            int overlayCoords, int lightCoords, float nx, float ny, float nz,
            CallbackInfo ci
    ) {
        if (this.format == XenoVertexFormat.XENO_COMPRESSED_FORMAT) {
            long pointer = this.beginVertex();

            // 1. Pack Position (RGBA16_SINT = 8 bytes)
            short posX = (short) Math.round(x * 1000.0f);
            short posY = (short) Math.round(y * 1000.0f);
            short posZ = (short) Math.round(z * 1000.0f);
            short posW = (short) xeno_getNormalId(nx, ny, nz);

            MemoryUtil.memPutShort(pointer, posX);
            MemoryUtil.memPutShort(pointer + 2L, posY);
            MemoryUtil.memPutShort(pointer + 4L, posZ);
            MemoryUtil.memPutShort(pointer + 6L, posW);

            // 2. Pack Color (RGBA8_UNORM = 4 bytes)
            putRgba(pointer + 8L, color);

            // 3. Pack UV0 (RG16_SINT = 4 bytes)
            short texU = (short) Math.round(u * 32767.0f);
            short texV = (short) Math.round(v * 32767.0f);
            MemoryUtil.memPutShort(pointer + 12L, texU);
            MemoryUtil.memPutShort(pointer + 14L, texV);

            // 4. Pack UV2 (RG16_SINT = 4 bytes)
            short lightBlock = (short) (lightCoords & 0xFFFF);
            short lightSky = (short) ((lightCoords >> 16) & 0xFFFF);
            MemoryUtil.memPutShort(pointer + 16L, lightBlock);
            MemoryUtil.memPutShort(pointer + 18L, lightSky);

            ci.cancel();
        }
    }

    @Unique
    private static int xeno_getNormalId(float nx, float ny, float nz) {
        float absX = Math.abs(nx);
        float absY = Math.abs(ny);
        float absZ = Math.abs(nz);

        if (absX > absY && absX > absZ) {
            return nx > 0.0f ? 5 : 4; // East or West
        } else if (absY > absX && absY > absZ) {
            return ny > 0.0f ? 1 : 0; // Up or Down
        } else {
            return nz > 0.0f ? 3 : 2; // South or North
        }
    }
}
