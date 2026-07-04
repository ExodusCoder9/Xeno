package com.xeno.client.mixin;

import com.xeno.client.culling.XenoVertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferBuilder.class)
@SuppressWarnings({"UnresolvedMixinReference", "unused"})
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

    //noinspection SpellCheckingInspection
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

    /**
     * Injected method override of putBlockBakedQuad from VertexConsumer.
     * Declared as a normal public method in the Mixin so it is injected into BufferBuilder as a standard override,
     * resolving the "@At / Cannot resolve target instructions in target class" errors.
     */
    @Override
    public void putBlockBakedQuad(final float x, final float y, final float z, final BakedQuad quad, final QuadInstance instance) {
        if (this.format == XenoVertexFormat.XENO_COMPRESSED_FORMAT) {
            if (!this.building) {
                throw new IllegalStateException("Not building!");
            }
            if (this.vertices >= 16777212) {
                throw new IllegalStateException("Trying to write too many vertices into BufferBuilder");
            }

            // Reserve 80 bytes (20 bytes per vertex * 4 vertices) at once
            long pointer = this.buffer.reserve(80);
            this.vertexPointer = pointer + 60L; // Point to the start of the 4th vertex
            this.vertices += 4;

            // Extract face normal and get normal ID once per quad
            Vector3fc normal = quad.direction().getUnitVec3f();
            short normalId = (short) xeno_getNormalId(normal.x(), normal.y(), normal.z());

            int lightEmission = quad.materialInfo().lightEmission();

            // Fully unroll the loop for all 4 vertices and write directly using raw offsets to eliminate redundant variable warnings.
            
            // Vertex 0
            {
                Vector3fc pos = quad.position(0);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryUtil.memPutShort(pointer, posX);
                MemoryUtil.memPutShort(pointer + 2L, posY);
                MemoryUtil.memPutShort(pointer + 4L, posZ);
                MemoryUtil.memPutShort(pointer + 6L, normalId);

                putRgba(pointer + 8L, instance.getColor(0));

                long packedUv = quad.packedUV(0);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryUtil.memPutShort(pointer + 12L, texU);
                MemoryUtil.memPutShort(pointer + 14L, texV);

                int light = instance.getLightCoordsWithEmission(0, lightEmission);
                MemoryUtil.memPutShort(pointer + 16L, (short) (light & 0xFFFF));
                MemoryUtil.memPutShort(pointer + 18L, (short) ((light >> 16) & 0xFFFF));
            }

            // Vertex 1
            {
                Vector3fc pos = quad.position(1);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryUtil.memPutShort(pointer + 20L, posX);
                MemoryUtil.memPutShort(pointer + 22L, posY);
                MemoryUtil.memPutShort(pointer + 24L, posZ);
                MemoryUtil.memPutShort(pointer + 26L, normalId);

                putRgba(pointer + 28L, instance.getColor(1));

                long packedUv = quad.packedUV(1);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryUtil.memPutShort(pointer + 32L, texU);
                MemoryUtil.memPutShort(pointer + 34L, texV);

                int light = instance.getLightCoordsWithEmission(1, lightEmission);
                MemoryUtil.memPutShort(pointer + 36L, (short) (light & 0xFFFF));
                MemoryUtil.memPutShort(pointer + 38L, (short) ((light >> 16) & 0xFFFF));
            }

            // Vertex 2
            {
                Vector3fc pos = quad.position(2);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryUtil.memPutShort(pointer + 40L, posX);
                MemoryUtil.memPutShort(pointer + 42L, posY);
                MemoryUtil.memPutShort(pointer + 44L, posZ);
                MemoryUtil.memPutShort(pointer + 46L, normalId);

                putRgba(pointer + 48L, instance.getColor(2));

                long packedUv = quad.packedUV(2);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryUtil.memPutShort(pointer + 52L, texU);
                MemoryUtil.memPutShort(pointer + 54L, texV);

                int light = instance.getLightCoordsWithEmission(2, lightEmission);
                MemoryUtil.memPutShort(pointer + 56L, (short) (light & 0xFFFF));
                MemoryUtil.memPutShort(pointer + 58L, (short) ((light >> 16) & 0xFFFF));
            }

            // Vertex 3
            {
                Vector3fc pos = quad.position(3);
                short posX = (short) Math.round((pos.x() + x) * 1000.0f);
                short posY = (short) Math.round((pos.y() + y) * 1000.0f);
                short posZ = (short) Math.round((pos.z() + z) * 1000.0f);
                MemoryUtil.memPutShort(pointer + 60L, posX);
                MemoryUtil.memPutShort(pointer + 62L, posY);
                MemoryUtil.memPutShort(pointer + 64L, posZ);
                MemoryUtil.memPutShort(pointer + 66L, normalId);

                putRgba(pointer + 68L, instance.getColor(3));

                long packedUv = quad.packedUV(3);
                short texU = (short) Math.round(UVPair.unpackU(packedUv) * 32767.0f);
                short texV = (short) Math.round(UVPair.unpackV(packedUv) * 32767.0f);
                MemoryUtil.memPutShort(pointer + 72L, texU);
                MemoryUtil.memPutShort(pointer + 74L, texV);

                int light = instance.getLightCoordsWithEmission(3, lightEmission);
                MemoryUtil.memPutShort(pointer + 76L, (short) (light & 0xFFFF));
                MemoryUtil.memPutShort(pointer + 78L, (short) ((light >> 16) & 0xFFFF));
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
