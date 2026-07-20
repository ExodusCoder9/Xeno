package com.xeno.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.xeno.client.renderer.DynamicUniformStorageExtensions;
import com.xeno.client.renderer.uniform.XenoUniformManager;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(DynamicUniformStorage.class)
public abstract class DynamicUniformStorageMixin<T extends DynamicUniformStorage.DynamicUniform>
        implements DynamicUniformStorageExtensions<T>, XenoUniformManager.UniformStorageState<T> {

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
    public int getBlockSize() {
        return this.blockSize;
    }

    @Override
    public MappableRingBuffer getRingBuffer() {
        return this.ringBuffer;
    }

    @Override
    public int getNextBlock() {
        return this.nextBlock;
    }

    @Override
    public void setNextBlock(int nextBlock) {
        this.nextBlock = nextBlock;
    }

    @Override
    public int getCapacity() {
        return this.capacity;
    }

    @Override
    public void setLastUniform(T uniform) {
        this.lastUniform = uniform;
    }

    @Override
    public String getLabel() {
        return this.label;
    }

    @Override
    public Logger getLogger() {
        return LOGGER;
    }

    @Override
    public void callResizeBuffers(int newCapacity) {
        this.resizeBuffers(newCapacity);
    }

    @Override
    public GpuBufferSlice[] xeno$writeUniforms(final List<T> uniforms) {
        return XenoUniformManager.writeUniforms(uniforms, this);
    }
}
