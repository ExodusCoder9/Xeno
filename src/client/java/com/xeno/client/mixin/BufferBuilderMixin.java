package com.xeno.client.mixin;

import com.xeno.client.culling.XenoVertexFormat;
import com.xeno.client.renderer.MemoryIntrinsics;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferBuilder.class)
@SuppressWarnings({"unused"})
public abstract class BufferBuilderMixin implements VertexConsumer {
    @Shadow @Final
    private VertexFormat format;

    @Shadow @Final
    private ByteBufferBuilder buffer;

    @Shadow
    private int vertices;

    @Shadow
    private long vertexPointer;

    @Shadow
    private boolean building;

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

            // 1. Pack Position (RGB16_SINT = 6 bytes, Offset 0)
            short posX = (short) Math.round(x * 1000.0f);
            short posY = (short) Math.round(y * 1000.0f);
            short posZ = (short) Math.round(z * 1000.0f);
            MemoryIntrinsics.putShort(pointer, posX);
            MemoryIntrinsics.putShort(pointer + 2L, posY);
            MemoryIntrinsics.putShort(pointer + 4L, posZ);

            // 2. Pack Color (RGBA8_UNORM = 4 bytes, Offset 6)
            putRgba(pointer + 6L, color);

            // 3. Pack UV0 (RG16_SINT = 4 bytes, Offset 10)
            short texU = (short) Math.round(u * 32767.0f);
            short texV = (short) Math.round(v * 32767.0f);
            MemoryIntrinsics.putShort(pointer + 10L, texU);
            MemoryIntrinsics.putShort(pointer + 12L, texV);

            // 4. Pack UV2 / Lightmap & Normal ID (RG8_SINT = 2 bytes, Offset 14)
            byte lightBlock = (byte) ((lightCoords & 0xFFFF) / 16);
            byte lightSky = (byte) (((lightCoords >> 16) & 0xFFFF) / 16);
            byte normalId = (byte) xeno_getNormalId(nx, ny, nz);

            MemoryIntrinsics.putByte(pointer + 14L, (byte) (lightBlock | (normalId << 4)));
            MemoryIntrinsics.putByte(pointer + 15L, lightSky);

            ci.cancel();
        }
    }

    /**
     * Injected method override of putBlockBakedQuad from VertexConsumer.
     * Declared as a normal public method in the Mixin so it is injected into BufferBuilder as a standard override.
     */
    @Override
    public void putBlockBakedQuad(final float x, final float y, final float z, final @NonNull BakedQuad quad, final @NonNull QuadInstance instance) {
        if (this.format == XenoVertexFormat.XENO_COMPRESSED_FORMAT) {
            if (!this.building) {
                throw new IllegalStateException("Not building!");
            }
            if (this.vertices >= 16777212) {
                throw new IllegalStateException("Trying to write too many vertices into BufferBuilder");
            }

            // Reserve 64 bytes (16 bytes per vertex * 4 vertices) at once
            long pointer = this.buffer.reserve(64);
            this.vertexPointer = pointer + 48L; // Point to the start of the 4th vertex (offset 48)
            this.vertices += 4;

            // Extract face normal and get normal ID once per quad
            Vector3fc normal = quad.direction().getUnitVec3f();
            byte normalId = (byte) xeno_getNormalId(normal.x(), normal.y(), normal.z());

            int lightEmission = quad.materialInfo().lightEmission();

            // Fully unroll the loop for all 4 vertices under the 16-byte packed layout.
            
            // Vertex 0 (Offset 0)
            {
                Vector3fc pos = quad.position(0);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryIntrinsics.putShort(pointer, posX);
                MemoryIntrinsics.putShort(pointer + 2L, posY);
                MemoryIntrinsics.putShort(pointer + 4L, posZ);

                putRgba(pointer + 6L, instance.getColor(0));

                long packedUv = quad.packedUV(0);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryIntrinsics.putShort(pointer + 10L, texU);
                MemoryIntrinsics.putShort(pointer + 12L, texV);

                int light = instance.getLightCoordsWithEmission(0, lightEmission);
                byte lightBlock = (byte) ((light & 0xFFFF) / 16);
                byte lightSky = (byte) (((light >> 16) & 0xFFFF) / 16);
                MemoryIntrinsics.putByte(pointer + 14L, (byte) (lightBlock | (normalId << 4)));
                MemoryIntrinsics.putByte(pointer + 15L, lightSky);
            }

            // Vertex 1 (Offset 16)
            {
                Vector3fc pos = quad.position(1);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryIntrinsics.putShort(pointer + 16L, posX);
                MemoryIntrinsics.putShort(pointer + 18L, posY);
                MemoryIntrinsics.putShort(pointer + 20L, posZ);

                putRgba(pointer + 22L, instance.getColor(1));

                long packedUv = quad.packedUV(1);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryIntrinsics.putShort(pointer + 26L, texU);
                MemoryIntrinsics.putShort(pointer + 28L, texV);

                int light = instance.getLightCoordsWithEmission(1, lightEmission);
                byte lightBlock = (byte) ((light & 0xFFFF) / 16);
                byte lightSky = (byte) (((light >> 16) & 0xFFFF) / 16);
                MemoryIntrinsics.putByte(pointer + 30L, (byte) (lightBlock | (normalId << 4)));
                MemoryIntrinsics.putByte(pointer + 31L, lightSky);
            }

            // Vertex 2 (Offset 32)
            {
                Vector3fc pos = quad.position(2);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryIntrinsics.putShort(pointer + 32L, posX);
                MemoryIntrinsics.putShort(pointer + 34L, posY);
                MemoryIntrinsics.putShort(pointer + 36L, posZ);

                putRgba(pointer + 38L, instance.getColor(2));

                long packedUv = quad.packedUV(2);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryIntrinsics.putShort(pointer + 42L, texU);
                MemoryIntrinsics.putShort(pointer + 44L, texV);

                int light = instance.getLightCoordsWithEmission(2, lightEmission);
                byte lightBlock = (byte) ((light & 0xFFFF) / 16);
                byte lightSky = (byte) (((light >> 16) & 0xFFFF) / 16);
                MemoryIntrinsics.putByte(pointer + 46L, (byte) (lightBlock | (normalId << 4)));
                MemoryIntrinsics.putByte(pointer + 47L, lightSky);
            }

            // Vertex 3 (Offset 48)
            {
                Vector3fc pos = quad.position(3);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryIntrinsics.putShort(pointer + 48L, posX);
                MemoryIntrinsics.putShort(pointer + 50L, posY);
                MemoryIntrinsics.putShort(pointer + 52L, posZ);

                putRgba(pointer + 54L, instance.getColor(3));

                long packedUv = quad.packedUV(3);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryIntrinsics.putShort(pointer + 58L, texU);
                MemoryIntrinsics.putShort(pointer + 60L, texV);

                int light = instance.getLightCoordsWithEmission(3, lightEmission);
                byte lightBlock = (byte) ((light & 0xFFFF) / 16);
                byte lightSky = (byte) (((light >> 16) & 0xFFFF) / 16);
                MemoryIntrinsics.putByte(pointer + 62L, (byte) (lightBlock | (normalId << 4)));
                MemoryIntrinsics.putByte(pointer + 63L, lightSky);
            }
        } else {
            // Fallback to the interface's default method implementation
            VertexConsumer.super.putBlockBakedQuad(x, y, z, quad, instance);
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
