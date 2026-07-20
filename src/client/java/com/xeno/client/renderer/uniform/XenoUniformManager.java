package com.xeno.client.renderer.uniform;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Custom manager that handles ring-buffer mapping, power-of-two capacity expansion,
 * and direct memory slice generation for dynamic shader uniforms.
 */
public class XenoUniformManager {

    public interface UniformStorageState<T extends DynamicUniformStorage.DynamicUniform> {
        int getBlockSize();
        MappableRingBuffer getRingBuffer();
        int getNextBlock();
        void setNextBlock(int nextBlock);
        int getCapacity();
        void setLastUniform(T uniform);
        String getLabel();
        Logger getLogger();
        void callResizeBuffers(int newCapacity);
    }

    public static <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice[] writeUniforms(
            List<T> uniforms,
            UniformStorageState<T> state
    ) {
        int size = uniforms.size();
        if (size == 0) {
            return new GpuBufferSlice[0];
        }

        int nextBlock = state.getNextBlock();
        int capacity = state.getCapacity();
        int blockSize = state.getBlockSize();
        MappableRingBuffer ringBuffer = state.getRingBuffer();

        if (nextBlock + size > capacity) {
            int newCapacity = Mth.smallestEncompassingPowerOfTwo(Math.max(capacity + 1, size));
            state.getLogger().info(
                    "Resizing {}, capacity limit of {} reached during a single frame. New capacity will be {}.",
                    state.getLabel(), capacity, newCapacity
            );
            state.callResizeBuffers(newCapacity);
        }

        int firstOffset = nextBlock * blockSize;
        GpuBufferSlice[] result = new GpuBufferSlice[size];

        try (GpuBufferSlice.MappedView view = ringBuffer.currentBuffer().slice(firstOffset, (long) size * blockSize).map(false, true)) {
            ByteBuffer byteBuffer = view.data();

            for (int i = 0; i < size; i++) {
                T uniform = uniforms.get(i);
                result[i] = ringBuffer.currentBuffer().slice(firstOffset + (long) i * blockSize, blockSize);
                byteBuffer.position(i * blockSize);
                uniform.write(byteBuffer);
            }
        }

        state.setNextBlock(nextBlock + size);
        state.setLastUniform(uniforms.get(size - 1));
        return result;
    }
}
