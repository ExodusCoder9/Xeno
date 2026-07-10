package com.xeno.client.mixin;

import com.xeno.client.XenoClient;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.system.MemoryUtil;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements VertexConsumer {
    @Shadow @Final private VertexFormat format;

    @Shadow
    private long beginVertex() {
        throw new AssertionError();
    }

    @Shadow
    private static void putRgba(long pointer, int color) {
        throw new AssertionError();
    }

    @Inject(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), cancellable = true)
    private void xenoAddVertex(
            float x, float y, float z, int color, float u, float v,
            int overlayCoords, int lightCoords, float nx, float ny, float nz,
            CallbackInfo ci
    ) {
        if (this.format == XenoClient.COMPRESSED_BLOCK_FORMAT) {
            long pointer = this.beginVertex();

            // 1. Position: 3 half-floats (6 bytes)
            short hx = Float.floatToFloat16(x);
            short hy = Float.floatToFloat16(y);
            short hz = Float.floatToFloat16(z);
            MemoryUtil.memPutShort(pointer, hx);
            MemoryUtil.memPutShort(pointer + 2L, hy);
            MemoryUtil.memPutShort(pointer + 4L, hz);

            // 2. Color: 4 bytes (4 bytes)
            putRgba(pointer + 6L, color);

            // 3. UV0: 2 half-floats (4 bytes)
            short hu = Float.floatToFloat16(u);
            short hv = Float.floatToFloat16(v);
            MemoryUtil.memPutShort(pointer + 10L, hu);
            MemoryUtil.memPutShort(pointer + 12L, hv);

            // 4. UV2: 2 bytes (2 bytes)
            byte bl = (byte) (lightCoords & 0xFF);
            byte sl = (byte) ((lightCoords >> 16) & 0xFF);
            MemoryUtil.memPutByte(pointer + 14L, bl);
            MemoryUtil.memPutByte(pointer + 15L, sl);

            ci.cancel();
        }
    }
}
