package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.slf4j.Logger;
import com.xeno.client.renderer.DynamicUniformStorageExtensions;
import java.nio.ByteBuffer;
import java.util.List;

@Mixin(DynamicUniformStorage.class)
public abstract class DynamicUniformStorageMixin<T extends DynamicUniformStorage.DynamicUniform> implements DynamicUniformStorageExtensions<T> {
    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private int blockSize;
    @Shadow private MappableRingBuffer ringBuffer;
    @Shadow private int nextBlock;
    @Shadow private int capacity;
    @Shadow private T lastUniform;
    @Shadow @Final private String label;

    @Shadow
    protected abstract void resizeBuffers(final int newCapacity);

    @Override
    public GpuBufferSlice[] xeno$writeUniforms(final List<T> uniforms) {
        int size = uniforms.size();
        if (size == 0) {
            return new GpuBufferSlice[0];
        }

        if (this.nextBlock + size > this.capacity) {
            int newCapacity = Mth.smallestEncompassingPowerOfTwo(Math.max(this.capacity + 1, size));
            LOGGER.info(
               "Resizing {}, capacity limit of {} reached during a single frame. New capacity will be {}.", this.label, this.capacity, newCapacity
            );
            this.resizeBuffers(newCapacity);
        }

        int firstOffset = this.nextBlock * this.blockSize;
        GpuBufferSlice[] result = new GpuBufferSlice[size];

        try (GpuBufferSlice.MappedView view = this.ringBuffer.currentBuffer().slice(firstOffset, (long) size * this.blockSize).map(false, true)) {
            ByteBuffer byteBuffer = view.data();

            for (int i = 0; i < size; i++) {
                T uniform = uniforms.get(i);
                result[i] = this.ringBuffer.currentBuffer().slice(firstOffset + (long) i * this.blockSize, this.blockSize);
                byteBuffer.position(i * this.blockSize);
                uniform.write(byteBuffer);
            }
        }

        this.nextBlock += size;
        this.lastUniform = uniforms.get(size - 1);
        return result;
    }
}
