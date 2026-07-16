package com.xeno.client.util;

import java.util.List;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class NonStoringBuilderPool extends SectionBufferBuilderPool {
    public NonStoringBuilderPool() {
        SectionBufferBuilderPack pack = new SectionBufferBuilderPack();
        pack.close();
        super(List.of(pack));
    }

    @Override
    public @Nullable SectionBufferBuilderPack acquire() {
        return null;
    }

    @Override
    public void release(@NonNull SectionBufferBuilderPack blockBufferBuilderStorage) {}

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int getFreeBufferCount() {
        return 0;
    }
}
